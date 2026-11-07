package com.briefwise.news.infrastructure.ai;

import com.briefwise.aiops.AiopsFacade;
import com.briefwise.news.domain.port.GenerationAudit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@link GenerationAudit} through {@code AiopsFacade}; a failure is logged, never thrown (the port's contract). */
@Component
class FacadeGenerationAudit implements GenerationAudit {

    private static final Logger log = LoggerFactory.getLogger(FacadeGenerationAudit.class);

    private final AiopsFacade aiops;

    FacadeGenerationAudit(AiopsFacade aiops) {
        this.aiops = aiops;
    }

    @Override
    public void markFallback(UUID generationId) {
        try {
            aiops.markFallback(generationId);
        } catch (RuntimeException e) {
            log.warn("Could not flag generation {} as fallback: {}", generationId, e.toString());
        }
    }
}
