package com.briefwise.market.domain.model;

import com.briefwise.shared.domain.exception.ResourceNotFoundException;
import com.briefwise.shared.domain.model.Ticker;

public class InstrumentNotFoundException extends ResourceNotFoundException {

    public InstrumentNotFoundException(Ticker ticker) {
        super("Instrument not found: " + ticker);
    }
}
