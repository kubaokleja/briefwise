package com.briefwise.portfolio.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface PortfolioJpaRepository extends JpaRepository<PortfolioEntity, UUID> {

    @EntityGraph(attributePaths = {"client", "positions"})
    Optional<PortfolioEntity> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"client", "positions"})
    List<PortfolioEntity> findAllByOrderByNameAsc();
}
