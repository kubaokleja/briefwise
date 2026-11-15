package com.briefwise.research.infrastructure.edgar;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * A minimum gap between the starts of two requests (ADR 0026: no rate-limiter library, sequential ingest). Called
 * before every attempt, retries included, so the SEC never sees more than one request per gap.
 */
final class EdgarThrottle {

    /** Waits for the given nanoseconds; {@link Thread#sleep} in production, recorded in tests. */
    interface Sleeper {
        void sleep(long nanos) throws InterruptedException;
    }

    private final long gapNanos;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private long lastStart;
    private boolean started;

    EdgarThrottle(Duration gap, LongSupplier nanoTime, Sleeper sleeper) {
        this.gapNanos = gap.toNanos();
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    static EdgarThrottle real(Duration gap) {
        return new EdgarThrottle(gap, System::nanoTime, nanos -> Thread.sleep(Duration.ofNanos(nanos)));
    }

    synchronized void awaitTurn() {
        if (started) {
            long wait = gapNanos - (nanoTime.getAsLong() - lastStart);
            if (wait > 0) {
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for the EDGAR request gap", e);
                }
            }
        }
        lastStart = nanoTime.getAsLong();
        started = true;
    }
}
