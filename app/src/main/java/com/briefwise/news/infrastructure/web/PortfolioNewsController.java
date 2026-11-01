package com.briefwise.news.infrastructure.web;

import com.briefwise.news.NewsFacade;
import com.briefwise.news.NewsFacade.PortfolioSummaryView;
import com.briefwise.news.NewsFacade.RelevantNewsView;
import com.briefwise.news.NewsFacade.SummaryPointView;
import com.briefwise.news.domain.model.SummaryNotFoundException;
import com.briefwise.shared.domain.model.Citation;
import com.briefwise.shared.domain.model.Ticker;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound adapter: news per portfolio. Calls the facade only, no business logic; reading a summary never calls the
 * LLM (the pipeline makes it, ADR 0017). Texts are third-party or model output: the UI renders them as text only.
 */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/news")
class PortfolioNewsController {

    private final NewsFacade facade;

    PortfolioNewsController(NewsFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/relevant")
    List<RelevantNewsResponse> relevant(@PathVariable UUID portfolioId) {
        return facade.getRelevantNews(portfolioId).stream().map(RelevantNewsResponse::from).toList();
    }

    @GetMapping("/summary")
    SummaryResponse summary(@PathVariable UUID portfolioId) {
        return facade.getLatestSummary(portfolioId).map(SummaryResponse::from)
                .orElseThrow(() -> new SummaryNotFoundException(portfolioId));
    }

    record RelevantNewsResponse(UUID id, String headline, String url, String source, Instant publishedAt,
                                List<String> tickers, BigDecimal weight, String sentiment, String relevance,
                                String eventType, String rationale) {
        static RelevantNewsResponse from(RelevantNewsView v) {
            return new RelevantNewsResponse(v.id(), v.headline(), v.url(), v.source(), v.publishedAt(),
                    v.heldTickers().stream().map(Ticker::symbol).toList(), v.weight(), v.sentiment().name(),
                    v.relevance().name(), v.eventType().name(), v.rationale());
        }
    }

    record CitationResponse(String type, String sourceId, String locator, String excerpt) {
        static CitationResponse from(Citation c) {
            return new CitationResponse(c.type().name(), c.sourceId(), c.locator(), c.excerpt());
        }
    }

    record PointResponse(String text, List<CitationResponse> citations) {
        static PointResponse from(SummaryPointView p) {
            return new PointResponse(p.text(), p.citations().stream().map(CitationResponse::from).toList());
        }
    }

    record ViolationResponse(String rule, String offendingText) {}

    /**
     * {@code aiGenerated} drives the AI marker; {@code fallback} the "built from a template" banner; {@code empty}
     * the "nothing relevant" state. {@code model}/{@code promptVersion} are null unless {@code aiGenerated}.
     */
    record SummaryResponse(UUID portfolioId, LocalDate summaryDate, Instant generatedAt, List<PointResponse> points,
                           String disclaimer, boolean aiGenerated, boolean fallback, boolean empty,
                           @Schema(types = {"string", "null"}) String model,
                           @Schema(types = {"string", "null"}) String promptVersion,
                           List<ViolationResponse> violations) {
        static SummaryResponse from(PortfolioSummaryView v) {
            return new SummaryResponse(v.portfolioId(), v.summaryDate(), v.generatedAt(),
                    v.points().stream().map(PointResponse::from).toList(), v.disclaimer(), v.aiGenerated(),
                    v.fallback(), v.empty(), v.model().orElse(null), v.promptVersion().orElse(null),
                    v.violations().stream().map(x -> new ViolationResponse(x.rule(), x.offendingText())).toList());
        }
    }
}
