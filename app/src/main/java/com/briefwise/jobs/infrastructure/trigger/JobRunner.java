package com.briefwise.jobs.infrastructure.trigger;

import com.briefwise.jobs.JobsFacade.JobRunView;
import com.briefwise.jobs.domain.model.JobTrigger;
import com.briefwise.jobs.domain.model.UnknownJobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Job runner for GitHub Actions (DESIGN 13.2, ADR 0016): {@code --briefwise.job=<id>} runs one job at startup. The
 * exit code is the contract with the workflow: 0 done (or the job is disabled), 1 a step failed or
 * {@code max-duration} was reached, 2 no such job. {@code BriefwiseApplication} exits with it once startup is over;
 * the runner itself never calls {@code System.exit}, so tests can read the code.
 */
@Component
@ConditionalOnProperty(prefix = "briefwise", name = "job")
class JobRunner implements ApplicationRunner, ExitCodeGenerator {

    static final int OK = 0;
    static final int FAILED = 1;
    static final int UNKNOWN_JOB = 2;

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final JobLauncher launcher;
    private final String jobId;
    private volatile int exitCode = FAILED;

    JobRunner(JobLauncher launcher, @Value("${briefwise.job}") String jobId) {
        this.launcher = launcher;
        this.jobId = jobId;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            exitCode = launcher.runIfEnabled(jobId, JobTrigger.RUNNER)
                    .map(JobRunView::status)
                    .map(status -> status.isFailure() ? FAILED : OK)
                    .orElse(OK);
        } catch (IllegalArgumentException | UnknownJobException e) {
            log.error("Job runner: {}", e.getMessage());
            exitCode = UNKNOWN_JOB;
        } catch (RuntimeException e) {
            log.error("Job runner: job {} failed", jobId, e);
            exitCode = FAILED;
        }
        log.info("Job runner: job {} exits with {}", jobId, exitCode);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
