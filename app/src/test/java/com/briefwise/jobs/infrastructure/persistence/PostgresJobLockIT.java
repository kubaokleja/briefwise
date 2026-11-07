package com.briefwise.jobs.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.port.JobLock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Advisory locks on the real Postgres: one session holds the lock, a second session is refused at once. */
class PostgresJobLockIT extends AbstractIntegrationTest {

    @Autowired
    PostgresJobLock locks;

    @Test
    void news_jobs_share_one_lock_and_the_eval_has_its_own() {
        Optional<JobLock.Held> ingest = locks.tryAcquire(JobName.NEWS_INGEST);
        assertThat(ingest).isPresent();
        try {
            assertThat(locks.tryAcquire(JobName.NEWS_INGEST)).isEmpty();
            assertThat(locks.tryAcquire(JobName.NEWS_CLASSIFY)).isEmpty();
            Optional<JobLock.Held> eval = locks.tryAcquire(JobName.NEWS_CLASSIFICATION_EVAL);
            assertThat(eval).isPresent();
            eval.get().close();
        } finally {
            ingest.get().close();
        }
    }

    @Test
    void a_released_lock_can_be_taken_again() {
        locks.tryAcquire(JobName.NEWS_CLASSIFY).orElseThrow().close();

        Optional<JobLock.Held> again = locks.tryAcquire(JobName.NEWS_CLASSIFY);

        assertThat(again).isPresent();
        again.get().close();
    }
}
