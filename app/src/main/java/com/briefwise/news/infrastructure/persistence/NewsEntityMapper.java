package com.briefwise.news.infrastructure.persistence;

import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.shared.domain.model.Ticker;
import java.util.Optional;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface NewsEntityMapper {

    @Mapping(target = "duplicateOf", source = "duplicateOfId")
    NewsItem toDomain(NewsItemEntity entity);

    default Ticker toTicker(String symbol) {
        return new Ticker(symbol);
    }

    default Optional<UUID> toOptional(UUID id) {
        return Optional.ofNullable(id);
    }
}
