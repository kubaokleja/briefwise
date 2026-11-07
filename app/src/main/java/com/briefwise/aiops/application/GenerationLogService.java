package com.briefwise.aiops.application;

import com.briefwise.aiops.domain.model.NewGeneration;
import com.briefwise.aiops.domain.model.PriceList;
import com.briefwise.aiops.domain.port.GenerationLog;
import com.briefwise.aiops.domain.port.GenerationRepository;
import com.briefwise.shared.domain.model.PricingTier;
import com.briefwise.shared.infrastructure.web.CorrelationIdFilter;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
class GenerationLogService implements GenerationLog {

    private final GenerationRepository generations;
    private final PriceList prices;

    GenerationLogService(GenerationRepository generations, PriceList prices) {
        this.generations = generations;
        this.prices = prices;
    }

    @Override
    public UUID record(NewGeneration generation, PricingTier tier) {
        BigDecimal cost = prices.priceOf(generation.model()).costOf(generation.tokens(), tier);
        Optional<String> correlationId = Optional.ofNullable(MDC.get(CorrelationIdFilter.MDC_KEY));
        return generations.save(generation, cost, correlationId);
    }

    @Override
    public void markFallback(UUID id) {
        if (!generations.markFallback(id)) {
            throw new IllegalArgumentException("No generation log entry " + id);
        }
    }
}
