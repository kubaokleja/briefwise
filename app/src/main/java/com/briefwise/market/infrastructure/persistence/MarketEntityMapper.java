package com.briefwise.market.infrastructure.persistence;

import com.briefwise.market.domain.model.DailyPrice;
import com.briefwise.market.domain.model.Instrument;
import com.briefwise.shared.domain.model.Ticker;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface MarketEntityMapper {

    @Mapping(target = "type", source = "instrumentType")
    Instrument toDomain(InstrumentEntity entity);

    @Mapping(target = "date", source = "tradeDate")
    DailyPrice toDomain(DailyPriceEntity entity);

    default Ticker toTicker(String symbol) {
        return new Ticker(symbol);
    }
}
