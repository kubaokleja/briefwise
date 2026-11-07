package com.briefwise.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.news.NewsFacade.ClassifyPendingView;
import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.UnclassifiedReason;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The sweep over several runs against the real queries (ADR 0016): each {@code classifyPending} call stands for one
 * daily run. Pins how the attempt cap ends a provider-error chain and a budget that is used up day after day.
 */
class NewsSweepIT extends AbstractIntegrationTest {

    @Autowired
    NewsFacade facade;

    @Autowired
    RecordingNewsClassifier classifier;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM news_outbox");
        jdbc.update("DELETE FROM news_items");
        classifier.reset();
    }

    /** An item past the sweep's min-age, with no outbox row (as after a DLT): sweepable. */
    private UUID storeOldReceived(String headline, int hoursAgo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO news_items (id, headline, url, url_hash, source, published_at, status, created_at)
                VALUES (?, ?, ?, ?, 'Synthetic Wire', now(), 'RECEIVED', now() - make_interval(hours => ?))""",
                id, headline, "https://news.example.com/" + id, id.toString().replace("-", ""), hoursAgo);
        jdbc.update("INSERT INTO news_item_tickers (news_item_id, ticker) VALUES (?, 'AAPL')", id);
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT status, unclassified_reason, sweep_attempts FROM news_items WHERE id = ?", id);
    }

    private long calls(UUID id) {
        return classifier.calls().stream().map(NewsItem::id).filter(id::equals).count();
    }

    private ClassifyPendingView run() {
        return facade.classifyPending(Instant.now().plusSeconds(600));
    }

    @Test
    void a_provider_error_is_retried_up_to_the_cap_then_stays_provider_error() {
        classifier.answer(item -> new ClassifierResult.Failed(UnclassifiedReason.PROVIDER_ERROR));
        UUID id = storeOldReceived("Apple supplier update", 1);

        assertThat(run().reopened()).isZero();
        assertThat(run().reopened()).isEqualTo(1);
        assertThat(run().reopened()).isEqualTo(1);
        ClassifyPendingView fourth = run();

        // sweep_attempts reached max-attempts (3): no more reopening, and the reason stays PROVIDER_ERROR, not
        // RETRIES_EXHAUSTED (only RECEIVED items give up, on the pick after the cap).
        assertThat(fourth.reopened()).isZero();
        assertThat(fourth.picked()).isZero();
        assertThat(row(id)).containsEntry("status", "UNCLASSIFIED")
                .containsEntry("unclassified_reason", "PROVIDER_ERROR")
                .containsEntry("sweep_attempts", 3);
        assertThat(calls(id)).isEqualTo(3);
    }

    @Test
    void a_budget_used_up_day_after_day_burns_one_attempt_per_run_on_the_oldest_item_until_it_gives_up() {
        classifier.answer(item -> new ClassifierResult.Deferred("daily budget for news-classification is used up"));
        UUID oldest = storeOldReceived("Apple oldest item", 3);
        UUID younger = storeOldReceived("Apple younger item", 2);

        for (int day = 1; day <= 3; day++) {
            ClassifyPendingView view = run();
            assertThat(view.stoppedOnDeferral()).isTrue();
            assertThat(view.picked()).isEqualTo(1);
        }
        assertThat(row(oldest)).containsEntry("status", "RECEIVED").containsEntry("sweep_attempts", 3);
        assertThat(row(younger)).containsEntry("sweep_attempts", 0);

        ClassifyPendingView fourth = run();

        // The 4th pick is past the cap: the oldest gives up without a call, the run moves on and defers on the next.
        assertThat(fourth.gaveUp()).isEqualTo(1);
        assertThat(row(oldest)).containsEntry("status", "UNCLASSIFIED")
                .containsEntry("unclassified_reason", "RETRIES_EXHAUSTED");
        assertThat(calls(oldest)).isEqualTo(3);
        assertThat(row(younger)).containsEntry("status", "RECEIVED").containsEntry("sweep_attempts", 1);
    }

    @Test
    void a_run_past_its_deadline_picks_nothing_and_leaves_the_items_for_the_next_run() {
        classifier.answer(item -> new ClassifierResult.Failed(UnclassifiedReason.PROVIDER_ERROR));
        UUID id = storeOldReceived("Apple late item", 1);

        ClassifyPendingView view = facade.classifyPending(Instant.now().minusSeconds(1));

        assertThat(view.stoppedOnDeadline()).isTrue();
        assertThat(view.picked()).isZero();
        assertThat(row(id)).containsEntry("status", "RECEIVED").containsEntry("sweep_attempts", 0);
    }
}
