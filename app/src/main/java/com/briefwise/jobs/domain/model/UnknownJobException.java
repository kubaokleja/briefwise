package com.briefwise.jobs.domain.model;

import com.briefwise.shared.domain.exception.ResourceNotFoundException;

/** No job has this id. */
public class UnknownJobException extends ResourceNotFoundException {

    public UnknownJobException(String id) {
        super("Unknown job: " + id);
    }
}
