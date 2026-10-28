package com.briefwise.jobs.domain.model;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Jobs that can be run on demand, by the in-app scheduler or by the job runner (DESIGN 13.2). The id is the key in
 * {@code briefwise.jobs.<id>} and the value of {@code --briefwise.job}. {@code news-ingest}: ingest, publish, (runner:
 * wait until consumed), sweep, portfolio summaries. {@code news-classify}: the sweep alone.
 */
public enum JobName {
    NEWS_INGEST("news-ingest"),
    NEWS_CLASSIFY("news-classify");

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    private final String id;

    JobName(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /**
     * @throws IllegalArgumentException if {@code id} is not a well-formed job id (lower case, digits, hyphens)
     * @throws UnknownJobException if no job has this id
     */
    public static JobName fromId(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Not a job id (lower case letters, digits, hyphens): " + id);
        }
        return Arrays.stream(values()).filter(job -> job.id.equals(id)).findFirst()
                .orElseThrow(() -> new UnknownJobException(id));
    }
}
