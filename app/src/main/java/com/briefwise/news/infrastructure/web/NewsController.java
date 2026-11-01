package com.briefwise.news.infrastructure.web;

import com.briefwise.news.NewsFacade;
import com.briefwise.news.NewsFacade.EventTypeView;
import com.briefwise.news.NewsFacade.NewsFeedItemView;
import com.briefwise.news.NewsFacade.NewsStatusView;
import com.briefwise.news.NewsFacade.RelevanceView;
import com.briefwise.news.NewsFacade.SentimentView;
import com.briefwise.news.NewsFacade.UnclassifiedReasonView;
import com.briefwise.shared.domain.model.Ticker;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound adapter: the news feed (M1 slice 9). Calls the facade only; never calls the LLM. Headlines are third-party
 * text and rationales are model output: the UI renders them as text only.
 */
@RestController
@RequestMapping("/api/news")
class NewsController {

    private final NewsFacade facade;

    NewsController(NewsFacade facade) {
        this.facade = facade;
    }

    @GetMapping
    List<NewsFeedItemResponse> feed(@RequestParam Optional<UUID> portfolioId,
                                    @RequestParam(defaultValue = "50") int limit) {
        return facade.getNewsFeed(portfolioId, limit).stream().map(NewsFeedItemResponse::from).toList();
    }

    /**
     * {@code status} drives the UI state: labels are null unless {@code CLASSIFIED}; {@code unclassifiedReason} is
     * null unless {@code UNCLASSIFIED}; a {@code RECEIVED} item has neither.
     */
    record NewsFeedItemResponse(
            UUID id, String headline, String url, String source, Instant publishedAt, NewsStatusView status,
            List<String> tickers,
            @Schema(types = {"string", "null"}) SentimentView sentiment,
            @Schema(types = {"string", "null"}) RelevanceView relevance,
            @Schema(types = {"string", "null"}) EventTypeView eventType,
            @Schema(types = {"string", "null"}) String rationale,
            @Schema(types = {"string", "null"}) UnclassifiedReasonView unclassifiedReason) {

        static NewsFeedItemResponse from(NewsFeedItemView v) {
            return new NewsFeedItemResponse(v.id(), v.headline(), v.url(), v.source(), v.publishedAt(), v.status(),
                    v.tickers().stream().map(Ticker::symbol).toList(), v.sentiment().orElse(null),
                    v.relevance().orElse(null), v.eventType().orElse(null), v.rationale().orElse(null),
                    v.unclassifiedReason().orElse(null));
        }
    }
}
