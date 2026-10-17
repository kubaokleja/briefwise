package com.briefwise.portfolio.infrastructure.persistence;

import com.briefwise.portfolio.domain.model.ClientId;
import com.briefwise.portfolio.domain.model.Portfolio;
import com.briefwise.portfolio.domain.model.Position;
import com.briefwise.shared.domain.model.Ticker;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface PortfolioEntityMapper {

    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "profile", source = "client.riskProfile")
    Portfolio toDomain(PortfolioEntity entity);

    Position toDomain(PositionEntity entity);

    default ClientId toClientId(UUID id) {
        return new ClientId(id);
    }

    default Ticker toTicker(String symbol) {
        return new Ticker(symbol);
    }
}
