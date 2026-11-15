package com.briefwise.research.domain.model;

import com.briefwise.shared.domain.exception.ResourceNotFoundException;

/** The source has no such filing (unknown accession number, or not one of the ingested forms). */
public class FilingNotFoundException extends ResourceNotFoundException {

    public FilingNotFoundException(String message) {
        super(message);
    }
}
