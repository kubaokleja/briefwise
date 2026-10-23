package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.HeldInstrument;
import java.util.List;

/** Outbound port: what the portfolios hold (implemented over the portfolio and market facades). */
public interface HeldInstruments {

    /** Each held ticker once, sorted by symbol. */
    List<HeldInstrument> list();
}
