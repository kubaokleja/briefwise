package com.briefwise.research.infrastructure.edgar;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.model.FilingNotFoundException;
import com.briefwise.research.domain.model.FilingRef;
import com.briefwise.research.domain.model.FilingSection;
import com.briefwise.research.domain.model.FilingSourceUnavailableException;
import com.briefwise.research.domain.model.FilingType;
import com.briefwise.research.domain.model.SectionExtractor;
import com.briefwise.research.domain.model.SectionPolicy;
import com.briefwise.research.domain.port.FilingSource;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Outbound adapter for SEC EDGAR (ADR 0026), active with {@code briefwise.research.filings.source=edgar} (a local
 * refresh only). SEC fair-access rules (checked 2026-11-15): at most 10 requests per second, a declared
 * {@code User-Agent} with a contact email, no API key.
 *
 * <ul>
 *   <li>{@link #list}: {@code data.sec.gov/submissions/CIK##########.json}, the {@code filings.recent} columns only.
 *       They hold at least one year (or 1,000 filings), which covers the latest 10-K and the 8-K lookback
 *       (ADR 0025); the older {@code filings.files} pages are not read.</li>
 *   <li>{@link #fetch}: the primary document from {@code /Archives/edgar/data/{cik}/{accession}/}; for an 8-K also
 *       the {@code {accession}-index.htm} page, whose document table names the EX-99.1 file in its Type column.</li>
 * </ul>
 * Every request waits for the {@link EdgarThrottle} gap. 429, 5xx and I/O errors are retried (ADR 0005); 403 means
 * an undeclared or blocked client and is a configuration error, not retried.
 */
@Component
@ConditionalOnProperty(prefix = "briefwise.research.filings", name = "source", havingValue = "edgar")
class EdgarFilingSource implements FilingSource {

    /** Both stages that shape the stored text: HTML to text and the section cut. */
    static final String EXTRACTOR_VERSION = "html-" + HtmlText.VERSION + "/sections-" + SectionExtractor.VERSION;

    private static final Logger log = LoggerFactory.getLogger(EdgarFilingSource.class);
    private static final String EX_99_1 = "EX-99.1";
    /** A file name inside the filing folder, never a path: the index page is untrusted input. */
    private static final Pattern DOCUMENT_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final EdgarProperties properties;
    private final RestClient client;
    private final RetryTemplate retry;
    private final EdgarThrottle throttle;
    private final SectionExtractor extractor = new SectionExtractor();

    @Autowired
    EdgarFilingSource(EdgarProperties properties) {
        this(properties, EdgarThrottle.real(properties.minRequestGap()));
    }

    EdgarFilingSource(EdgarProperties properties, EdgarThrottle throttle) {
        this.properties = properties;
        this.throttle = throttle;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", properties.userAgent())
                .build();
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .includes(TransientFailure.class)
                .maxRetries(properties.maxRetries())
                .delay(properties.retryDelay())
                .multiplier(2)
                .build());
    }

    @Override
    public List<FilingRef> list(Cik cik) {
        URI uri = UriComponentsBuilder.fromUriString(properties.dataBaseUrl())
                .pathSegment("submissions", "CIK" + cik.value() + ".json").build().encode().toUri();
        Submissions submissions = get(uri, Submissions.class, "submissions of CIK " + cik);
        if (submissions == null || submissions.filings() == null || submissions.filings().recent() == null) {
            throw new FilingSourceUnavailableException("EDGAR submissions of CIK " + cik + " have no recent filings",
                    null);
        }
        List<FilingRef> refs = submissions.filings().recent().toRefs(cik);
        if (refs.stream().noneMatch(ref -> ref.type() == FilingType.TEN_K)) {
            log.warn("No 10-K among the recent EDGAR filings of CIK {}", cik);
        }
        return refs;
    }

    @Override
    public FetchedFiling fetch(FilingRef ref) {
        URI primary = documentUri(ref, ref.primaryDocument());
        List<FilingSection> sections = new ArrayList<>(
                extractor.extract(ref.type(), HtmlText.of(document(primary, ref))));
        if (SectionPolicy.keepsExhibit991(ref.type())) {
            exhibit991Name(ref).ifPresent(name -> extractor
                    .exhibit991(HtmlText.of(document(documentUri(ref, name), ref)))
                    .ifPresent(sections::add));
        }
        return new FetchedFiling(ref, primary.toString(), EXTRACTOR_VERSION, sections);
    }

    private Optional<String> exhibit991Name(FilingRef ref) {
        Document index = document(documentUri(ref, ref.accessionNo().value() + "-index.htm"), ref);
        for (Element row : index.select("table.tableFile tr")) {
            Elements cells = row.select("> td");
            if (cells.size() >= 4 && cells.get(3).text().strip().equalsIgnoreCase(EX_99_1)) {
                String name = cells.get(2).select("a").text().strip();
                if (DOCUMENT_NAME.matcher(name).matches()) {
                    return Optional.of(name);
                }
                log.warn("Ignoring EX-99.1 of {} with an unexpected document name '{}'", ref.accessionNo(), name);
            }
        }
        return Optional.empty();
    }

    /** {@code documentName} is one file name, or a relative path as EDGAR lists some primary documents. */
    private URI documentUri(FilingRef ref, String documentName) {
        return UriComponentsBuilder.fromUriString(properties.archiveBaseUrl())
                .pathSegment("Archives", "edgar", "data", ref.cik().unpadded(), ref.accessionNo().withoutDashes())
                .pathSegment(documentName.split("/"))
                .build().encode().toUri();
    }

    private Document document(URI uri, FilingRef ref) {
        byte[] body = get(uri, byte[].class, "document of " + ref.accessionNo());
        if (body == null || body.length == 0) {
            throw new FilingSourceUnavailableException("Empty EDGAR response for " + uri, null);
        }
        return HtmlText.parse(body, uri.toString());
    }

    private <T> T get(URI uri, Class<T> type, String what) {
        if (properties.userAgent().isBlank()) {
            throw new IllegalStateException(
                    "No SEC User-Agent: set SEC_USER_AGENT to 'Name contact@email' (briefwise.research.edgar.user-agent)");
        }
        try {
            return retry.invoke(() -> call(uri, type));
        } catch (TransientFailure e) {
            throw new FilingSourceUnavailableException("EDGAR " + what + ": " + e.getMessage(), e);
        } catch (NotFound e) {
            throw new FilingNotFoundException("EDGAR has no " + what + " (" + uri + ")");
        } catch (RestClientException e) {
            // Other 4xx, or a body that is not the expected JSON: retrying would not help.
            throw new FilingSourceUnavailableException("EDGAR " + what + ": unexpected response: " + e.getMessage(), e);
        }
    }

    private <T> T call(URI uri, Class<T> type) {
        throttle.awaitTurn();
        try {
            return client.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(status -> status.value() == 403, (request, response) -> {
                        throw new IllegalStateException("EDGAR refused the request (HTTP 403): check SEC_USER_AGENT "
                                + "and the request rate; the SEC blocks undeclared or too fast clients");
                    })
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new NotFound();
                    })
                    .onStatus(status -> status.value() == 429 || status.is5xxServerError(), (request, response) -> {
                        throw new TransientFailure("HTTP " + response.getStatusCode().value(), null);
                    })
                    .body(type);
        } catch (ResourceAccessException e) {
            throw new TransientFailure("I/O error: " + e.getMessage(), e);
        }
    }

    /** {@code {"filings": {"recent": {...}}}}; all other fields are ignored. */
    record Submissions(Filings filings) {
    }

    record Filings(Recent recent) {
    }

    /** Columnar: entry {@code i} of every list belongs to the same filing. */
    record Recent(List<String> accessionNumber, List<String> filingDate, List<String> reportDate, List<String> form,
                  List<String> primaryDocument, List<String> items) {

        List<FilingRef> toRefs(Cik cik) {
            if (accessionNumber == null || filingDate == null || form == null || primaryDocument == null) {
                throw new FilingSourceUnavailableException("EDGAR submissions of CIK " + cik
                        + " miss a required column", null);
            }
            List<FilingRef> refs = new ArrayList<>();
            for (int i = 0; i < accessionNumber.size(); i++) {
                int row = i;
                FilingType.fromForm(at(form, row)).ifPresent(type -> {
                    try {
                        refs.add(new FilingRef(new AccessionNo(at(accessionNumber, row)), cik, type,
                                LocalDate.parse(at(filingDate, row)), date(at(reportDate, row)),
                                at(primaryDocument, row), items(at(items, row))));
                    } catch (RuntimeException e) {
                        log.warn("Dropping EDGAR row {} of CIK {}: {}", row, cik, e.getMessage());
                    }
                });
            }
            return refs;
        }

        private static String at(List<String> column, int row) {
            return column == null || row >= column.size() || column.get(row) == null ? "" : column.get(row);
        }

        private static Optional<LocalDate> date(String value) {
            return value.isBlank() ? Optional.empty() : Optional.of(LocalDate.parse(value));
        }

        private static Set<String> items(String value) {
            return Arrays.stream(value.split(","))
                    .map(String::strip)
                    .filter(item -> !item.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /** Worth another attempt: rate limit, server error, timeout or connection failure. */
    private static final class TransientFailure extends RuntimeException {

        TransientFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class NotFound extends RuntimeException {

        NotFound() {
            super("HTTP 404", null, false, false);
        }
    }
}
