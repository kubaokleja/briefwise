package com.briefwise.news.infrastructure.ai;

import com.briefwise.news.domain.model.SummaryDraft;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The model's summary; {@code BeanOutputConverter} turns this record into the JSON schema the provider enforces
 * (native structured output). Citations are item ids, checked in code against the request: a model can only cite
 * what it was given ("citation by id"), and the URL and headline of a citation come from our database, not from the
 * model.
 */
record SummaryAnswer(@JsonPropertyDescription("At most 5 points, most important first") List<Point> points) {

    @JsonPropertyOrder({"text", "newsIds"})
    record Point(@JsonPropertyDescription("One or two neutral sentences, at most 50 words") String text,
                 @JsonPropertyDescription("Ids of the news items this point relies on, copied exactly")
                 List<String> newsIds) {}

    /**
     * Checks what the schema cannot: 1 to {@code maxPoints} points, each with text and at least one id from the
     * request. Ids are deduplicated per point.
     *
     * @throws IllegalArgumentException if any check fails (invalid output: re-ask, then fallback)
     */
    SummaryDraft toDraft(Set<UUID> allowedIds, int maxPoints, String model, String promptVersion) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("No points in " + this);
        }
        if (points.size() > maxPoints) {
            throw new IllegalArgumentException(points.size() + " points, at most " + maxPoints + " allowed");
        }
        return new SummaryDraft(points.stream().map(p -> toPoint(p, allowedIds)).toList(), model, promptVersion);
    }

    private static SummaryDraft.Point toPoint(Point point, Set<UUID> allowedIds) {
        if (point == null || point.text() == null || point.text().isBlank()) {
            throw new IllegalArgumentException("A point without text");
        }
        if (point.newsIds() == null || point.newsIds().isEmpty()) {
            throw new IllegalArgumentException("Point cites no item: " + point.text());
        }
        List<UUID> ids = point.newsIds().stream().map(id -> parse(id, allowedIds)).distinct().toList();
        return new SummaryDraft.Point(point.text().strip(), ids);
    }

    private static UUID parse(String id, Set<UUID> allowedIds) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id == null ? "" : id.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not an item id: " + id, e);
        }
        if (!allowedIds.contains(uuid)) {
            throw new IllegalArgumentException("Cites item " + uuid + " that was not in the request");
        }
        return uuid;
    }
}
