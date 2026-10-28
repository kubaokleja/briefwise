package com.briefwise.jobs.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** One step of a run: what it reported ({@code result}, e.g. counts) or why it did not succeed ({@code message}). */
public record JobStep(String name, JobStatus status, Map<String, Object> result, Optional<String> message) {

    public JobStep {
        result = Collections.unmodifiableMap(new LinkedHashMap<>(result));   // keeps the reported order
    }

    public static JobStep succeeded(String name, Map<String, Object> result) {
        return new JobStep(name, JobStatus.SUCCEEDED, result, Optional.empty());
    }

    public static JobStep of(String name, JobStatus status, String message) {
        return new JobStep(name, status, Map.of(), Optional.of(message));
    }
}
