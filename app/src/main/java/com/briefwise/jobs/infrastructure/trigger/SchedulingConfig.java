package com.briefwise.jobs.infrastructure.trigger;

import com.briefwise.jobs.domain.model.JobName;
import com.briefwise.jobs.domain.model.JobTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * In-app scheduler (DESIGN 13.2): only with {@code briefwise.execution.mode=scheduled}, for an always-on host if one
 * is ever used. The daily pipeline runs in GitHub Actions through the job runner instead (ADR 0003).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "briefwise.execution", name = "mode", havingValue = "scheduled")
class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    private final JobLauncher launcher;

    SchedulingConfig(JobLauncher launcher) {
        this.launcher = launcher;
    }

    @Scheduled(cron = "${briefwise.jobs.news-ingest.cron}")
    void newsIngest() {
        try {
            launcher.runIfEnabled(JobName.NEWS_INGEST.id(), JobTrigger.SCHEDULER);
        } catch (RuntimeException e) {
            log.warn("Scheduled job {} failed: {}", JobName.NEWS_INGEST.id(), e.getMessage());
        }
    }
}
