package com.briefwise.news.application;

import com.briefwise.news.domain.model.FetchedNews;
import com.briefwise.news.domain.model.HeldInstrument;
import com.briefwise.news.domain.model.IngestPolicy;
import com.briefwise.news.domain.model.IngestSummary;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsSourceUnavailableException;
import com.briefwise.news.domain.model.UrlHash;
import com.briefwise.news.domain.port.HeldInstruments;
import com.briefwise.news.domain.port.NewsIngest;
import com.briefwise.news.domain.port.NewsRepository;
import com.briefwise.news.domain.port.NewsSource;
import com.briefwise.shared.domain.model.Ticker;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fetch, deduplicate, store (DESIGN 7.1, first three steps). Not one transaction: each item is stored on its own,
 * so a failure halfway keeps what was stored and the next run picks up the rest (URL hash makes it idempotent).
 * Outbox row and Kafka publish come in M1 slice 3.
 */
@Service
class NewsIngestService implements NewsIngest {

    private static final Logger log = LoggerFactory.getLogger(NewsIngestService.class);

    private enum Outcome { INSERTED, NEAR_DUPLICATE, ALREADY_KNOWN }

    private final HeldInstruments held;
    private final NewsSource source;
    private final NewsRepository repository;
    private final IngestPolicy policy;
    private final Clock clock;

    NewsIngestService(HeldInstruments held, NewsSource source, NewsRepository repository, IngestPolicy policy,
                      Clock clock) {
        this.held = held;
        this.source = source;
        this.repository = repository;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public IngestSummary ingest() {
        LocalDate to = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate from = to.minusDays(policy.lookbackDays());
        List<Ticker> queried = new ArrayList<>();
        List<Ticker> skipped = new ArrayList<>();
        List<Ticker> failed = new ArrayList<>();
        int fetched = 0;
        int inserted = 0;
        int nearDuplicates = 0;
        int alreadyKnown = 0;

        for (HeldInstrument instrument : held.list()) {
            Ticker ticker = instrument.ticker();
            if (instrument.kind() == HeldInstrument.Kind.FUND) {
                skipped.add(ticker); // no company news for ETFs yet (ADR 0009)
                continue;
            }
            List<FetchedNews> articles;
            try {
                articles = source.fetch(ticker, from, to);
            } catch (NewsSourceUnavailableException e) {
                log.warn("Skipping {} in this run: {}", ticker, e.getMessage());
                failed.add(ticker);
                continue;
            }
            queried.add(ticker);
            fetched += articles.size();
            // Oldest first, so the earliest version of a story becomes the original and later ones its duplicates.
            for (FetchedNews article : articles.stream().sorted(Comparator.comparing(FetchedNews::publishedAt)).toList()) {
                switch (store(article)) {
                    case INSERTED -> inserted++;
                    case NEAR_DUPLICATE -> nearDuplicates++;
                    case ALREADY_KNOWN -> alreadyKnown++;
                }
            }
        }
        IngestSummary summary = new IngestSummary(queried, skipped, failed, fetched, inserted, nearDuplicates,
                alreadyKnown);
        log.info("News ingest {}..{}: {}", from, to, summary);
        return summary;
    }

    private Outcome store(FetchedNews article) {
        UrlHash urlHash = UrlHash.of(article.url());
        Optional<UUID> known = repository.findIdByUrlHash(urlHash);
        if (known.isPresent()) {
            repository.addTicker(known.get(), article.ticker());
            return Outcome.ALREADY_KNOWN;
        }
        Optional<UUID> original = repository.findNearDuplicate(article.ticker(), article.headline(),
                article.publishedAt(), policy.dedup());
        repository.insert(NewsItem.fetched(UUID.randomUUID(), article, original), urlHash);
        return original.isPresent() ? Outcome.NEAR_DUPLICATE : Outcome.INSERTED;
    }
}
