package com.briefwise.research.domain.model;

/** The filing source could not deliver (after retries). Ingest counts it and goes on with the next filing. */
public class FilingSourceUnavailableException extends RuntimeException {

    public FilingSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
