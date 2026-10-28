package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.model.Citation;
import com.briefwise.shared.domain.model.SourceType;
import com.briefwise.shared.domain.model.Ticker;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a model draft or the template into cited summary points. A citation points to the news item (id), with its
 * URL as locator and its headline as excerpt, so the RM can open the source.
 */
public final class SummaryComposer {

    private SummaryComposer() {
    }

    /**
     * @throws IllegalArgumentException if the draft cites an item that was not in the selection (the adapter checks
     *                                  this before, so it means a bug)
     */
    public static List<SummaryPoint> cite(SummaryDraft draft, List<RelevantNews> selection) {
        Map<UUID, ClassifiedNews> byId = selection.stream().map(RelevantNews::news)
                .collect(Collectors.toMap(ClassifiedNews::id, Function.identity()));
        return draft.points().stream().map(point -> new SummaryPoint(point.text(), point.newsIds().stream()
                .map(id -> {
                    ClassifiedNews news = byId.get(id);
                    if (news == null) {
                        throw new IllegalArgumentException("Draft cites news item " + id + " outside the selection");
                    }
                    return citation(news);
                }).toList())).toList();
    }

    /**
     * The template fallback (graceful degradation, no LLM): one point per selected item, "AAPL, MSFT: headline
     * (negative, earnings)", citing that item. Headlines are third-party text quoted verbatim.
     */
    public static List<SummaryPoint> template(List<RelevantNews> selection) {
        return selection.stream().map(r -> {
            ClassifiedNews n = r.news();
            String tickers = r.heldTickers().stream().map(Ticker::symbol).sorted()
                    .collect(Collectors.joining(", "));
            String text = tickers + ": " + n.headline().strip() + " (" + label(n.sentiment()) + ", "
                    + label(n.eventType()) + ")";
            return new SummaryPoint(text, List.of(citation(n)));
        }).toList();
    }

    private static Citation citation(ClassifiedNews news) {
        return new Citation(SourceType.NEWS, news.id().toString(), news.url(), news.headline());
    }

    private static String label(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace("m_and_a", "M&A").replace('_', ' ');
    }
}
