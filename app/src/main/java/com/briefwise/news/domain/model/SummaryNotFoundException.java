package com.briefwise.news.domain.model;

import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import java.util.UUID;

/** The portfolio exists but has no news summary yet (the pipeline has not run for it). */
public class SummaryNotFoundException extends ResourceNotFoundException {

    public SummaryNotFoundException(UUID portfolioId) {
        super("No news summary for portfolio " + portfolioId + " yet");
    }
}
