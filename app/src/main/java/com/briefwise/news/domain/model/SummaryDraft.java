package com.briefwise.news.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A model's summary before the compliance check. Each point cites news items by id; the adapter has already
 * checked that every id was in the request.
 */
public record SummaryDraft(List<Point> points, String model, String promptVersion) {

    public record Point(String text, List<UUID> newsIds) {

        public Point {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("A summary point needs text");
            }
            newsIds = List.copyOf(Objects.requireNonNull(newsIds, "newsIds"));
        }
    }

    public SummaryDraft {
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (points.isEmpty()) {
            throw new IllegalArgumentException("A summary draft has at least one point");
        }
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(promptVersion, "promptVersion");
    }
}
