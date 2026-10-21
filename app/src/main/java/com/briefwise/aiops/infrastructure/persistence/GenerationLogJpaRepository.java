package com.briefwise.aiops.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GenerationLogJpaRepository extends JpaRepository<GenerationLogEntity, UUID> {
}
