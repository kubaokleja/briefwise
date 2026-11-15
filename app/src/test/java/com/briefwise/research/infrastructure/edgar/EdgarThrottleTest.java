package com.briefwise.research.infrastructure.edgar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fake clock and sleeper: no real waiting. */
class EdgarThrottleTest {

    private static final long MS = 1_000_000L;

    private long now = 1_000 * MS;
    private final List<Long> sleeps = new ArrayList<>();
    private final EdgarThrottle throttle = new EdgarThrottle(Duration.ofMillis(150), () -> now, nanos -> {
        sleeps.add(nanos);
        now += nanos;
    });

    @Test
    void first_request_goes_at_once() {
        throttle.awaitTurn();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void next_request_waits_for_the_rest_of_the_gap() {
        throttle.awaitTurn();
        now += 40 * MS;

        throttle.awaitTurn();

        assertThat(sleeps).containsExactly(110 * MS);
    }

    @Test
    void no_wait_once_the_gap_has_passed() {
        throttle.awaitTurn();
        now += 150 * MS;
        throttle.awaitTurn();
        now += 500 * MS;
        throttle.awaitTurn();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void the_gap_counts_from_the_start_after_a_wait() {
        throttle.awaitTurn();
        throttle.awaitTurn();     // waits 150 ms
        now += 100 * MS;

        throttle.awaitTurn();     // 100 ms after the previous start: 50 ms left

        assertThat(sleeps).containsExactly(150 * MS, 50 * MS);
    }
}
