package com.briefwise.jobs.infrastructure.news;

import com.briefwise.jobs.domain.model.EvalResult;
import com.briefwise.jobs.domain.model.PublishResult;
import com.briefwise.jobs.domain.port.NewsPipeline;
import com.briefwise.news.NewsFacade;
import com.briefwise.news.NewsFacade.ClassificationEvalView;
import com.briefwise.news.NewsFacade.ClassifyPendingView;
import com.briefwise.news.NewsFacade.IngestSummaryView;
import com.briefwise.news.NewsFacade.PublishSummaryView;
import com.briefwise.news.NewsFacade.SummaryRunView;
import com.briefwise.shared.domain.model.Ticker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The news steps of a job, only through {@link NewsFacade}; the views are reported as plain maps of counts. */
@Component
class FacadeNewsPipeline implements NewsPipeline {

    private final NewsFacade news;

    FacadeNewsPipeline(NewsFacade news) {
        this.news = news;
    }

    @Override
    public Map<String, Object> ingest() {
        IngestSummaryView s = news.ingest();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queried", s.queried().size());
        result.put("fetched", s.fetched());
        result.put("inserted", s.inserted());
        result.put("nearDuplicates", s.nearDuplicates());
        result.put("alreadyKnown", s.alreadyKnown());
        result.put("skippedTickers", symbols(s.skipped()));
        result.put("failedTickers", symbols(s.failed()));
        return result;
    }

    @Override
    public PublishResult publishPending() {
        PublishSummaryView s = news.publishPending();
        return new PublishResult(s.published(), s.pending(), s.brokerUnavailable());
    }

    @Override
    public long consumerLag() {
        return news.consumerLag();
    }

    @Override
    public Map<String, Object> classifyPending() {
        ClassifyPendingView s = news.classifyPending();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reopened", s.reopened());
        result.put("picked", s.picked());
        result.put("classified", s.classified());
        result.put("unclassified", s.unclassified());
        result.put("skipped", s.skipped());
        result.put("alreadyProcessed", s.alreadyProcessed());
        result.put("pending", s.pending());
        result.put("gaveUp", s.gaveUp());
        result.put("failed", s.failed());
        result.put("stoppedOnDeferral", s.stoppedOnDeferral());
        return result;
    }

    @Override
    public Map<String, Object> summarizePortfolios() {
        SummaryRunView s = news.summarizePortfolios();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portfolios", s.portfolios());
        result.put("generated", s.generated());
        result.put("fallback", s.fallback());
        result.put("empty", s.empty());
        result.put("unchanged", s.unchanged());
        result.put("deferred", s.deferred());
        result.put("failed", s.failed());
        result.put("stoppedOnDeferral", s.stoppedOnDeferral());
        return result;
    }

    @Override
    public EvalResult evaluateClassification() {
        ClassificationEvalView e = news.evaluateClassification();
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("status", e.status().name());
        figures.put("cases", e.cases());
        figures.put("invalid", e.invalid());
        e.sentimentMacroF1().ifPresent(f1 -> figures.put("sentimentMacroF1", f1));
        e.baselineSentimentMacroF1().ifPresent(f1 -> figures.put("baselineSentimentMacroF1", f1));
        e.costUsd().ifPresent(cost -> figures.put("costUsd", cost));
        e.batchId().ifPresent(id -> figures.put("batchId", id));
        return new EvalResult(e.passed(), e.message(), figures);
    }

    private static List<String> symbols(List<Ticker> tickers) {
        return tickers.stream().map(Ticker::symbol).toList();
    }
}
