package com.briefwise.market.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InstrumentJpaRepository extends JpaRepository<InstrumentEntity, UUID> {

    Optional<InstrumentEntity> findByTicker(String ticker);
}
