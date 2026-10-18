package com.briefwise.market.domain.port;

import com.briefwise.market.domain.model.DailyPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Outbound port: stored daily closes. */
public interface PriceRepository {

    /** Closes between the dates (inclusive), oldest first. */
    List<DailyPrice> findBetween(UUID instrumentId, LocalDate from, LocalDate to);

    /** The {@code count} most recent closes, newest first. */
    List<DailyPrice> findLatest(UUID instrumentId, int count);

    /**
     * Stores the closes whose date is not stored yet for the instrument; stored closes are not changed.
     *
     * @return how many closes were inserted
     */
    int insertMissing(UUID instrumentId, List<DailyPrice> prices);
}
