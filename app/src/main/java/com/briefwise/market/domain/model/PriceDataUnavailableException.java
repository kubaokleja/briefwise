package com.briefwise.market.domain.model;

import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import com.briefwise.shared.domain.model.Ticker;

/** The instrument exists but has too few stored closes for the requested figure. */
public class PriceDataUnavailableException extends ResourceNotFoundException {

    public PriceDataUnavailableException(Ticker ticker, String detail) {
        super("No price data for " + ticker + ": " + detail);
    }
}
