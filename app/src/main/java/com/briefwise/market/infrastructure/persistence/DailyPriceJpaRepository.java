package com.briefwise.market.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface DailyPriceJpaRepository extends JpaRepository<DailyPriceEntity, UUID> {

    List<DailyPriceEntity> findByInstrumentIdAndTradeDateBetweenOrderByTradeDateAsc(
            UUID instrumentId, LocalDate from, LocalDate to);

    List<DailyPriceEntity> findByInstrumentIdOrderByTradeDateDesc(UUID instrumentId, Pageable pageable);
}
