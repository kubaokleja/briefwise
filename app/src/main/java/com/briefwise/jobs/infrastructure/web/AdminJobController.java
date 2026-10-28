package com.briefwise.jobs.infrastructure.web;

import com.briefwise.jobs.JobsFacade;
import com.briefwise.jobs.JobsFacade.JobRunView;
import com.briefwise.jobs.domain.model.JobTrigger;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound adapter: "run now" for a job (DESIGN 13.2); calls the facade only. Runs synchronously and ignores
 * {@code enabled} (on demand is always allowed). The answer is 200 with the run's status even if a step failed: the
 * request did what it was asked. Unknown job 404, malformed id 400.
 * <p>
 * TODO(M4): role {@code ADMIN}. Open until login exists (ADR 0016): the app runs only locally (ADR 0003) and the daily
 * LLM budget caps what a run can cost.
 */
@RestController
@RequestMapping("/api/admin/jobs")
class AdminJobController {

    private final JobsFacade jobs;

    AdminJobController(JobsFacade jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/{job}/run")
    JobRunView runJob(@PathVariable String job) {
        return jobs.run(job, JobTrigger.ADMIN);
    }
}
