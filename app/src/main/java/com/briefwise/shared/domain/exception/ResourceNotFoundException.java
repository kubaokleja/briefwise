package com.briefwise.shared.domain.exception;

/** Base for "the requested thing does not exist". Inbound adapters translate it to HTTP 404. */
public abstract class ResourceNotFoundException extends RuntimeException {

    protected ResourceNotFoundException(String message) {
        super(message);
    }
}
