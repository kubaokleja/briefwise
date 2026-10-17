package com.briefwise.portfolio.domain.model;

import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import java.util.UUID;

public class PortfolioNotFoundException extends ResourceNotFoundException {

    public PortfolioNotFoundException(UUID id) {
        super("Portfolio not found: " + id);
    }
}
