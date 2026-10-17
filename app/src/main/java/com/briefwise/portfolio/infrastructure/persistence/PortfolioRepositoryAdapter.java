package com.briefwise.portfolio.infrastructure.persistence;

import com.briefwise.portfolio.domain.model.Portfolio;
import com.briefwise.portfolio.domain.port.PortfolioRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Outbound adapter. Mapping to domain records happens inside the transaction, so lazy associations are safe. */
@Component
class PortfolioRepositoryAdapter implements PortfolioRepository {

    private final PortfolioJpaRepository jpa;
    private final PortfolioEntityMapper mapper;

    PortfolioRepositoryAdapter(PortfolioJpaRepository jpa, PortfolioEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Portfolio> findById(UUID portfolioId) {
        return jpa.findWithDetailsById(portfolioId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Portfolio> findAll() {
        return jpa.findAllByOrderByNameAsc().stream().map(mapper::toDomain).toList();
    }
}
