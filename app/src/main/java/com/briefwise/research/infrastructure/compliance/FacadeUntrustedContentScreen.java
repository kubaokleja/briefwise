package com.briefwise.research.infrastructure.compliance;

import com.briefwise.compliance.ComplianceFacade;
import com.briefwise.compliance.ComplianceFacade.ScreeningView;
import com.briefwise.research.domain.model.Screening;
import com.briefwise.research.domain.port.UntrustedContentScreen;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The ADR 0013 pattern list on filing text at ingest. A match publishes {@code InjectionSuspected} with the section
 * id; the section is stored and flagged, and retrieval leaves it out (slice 3).
 */
@Component
class FacadeUntrustedContentScreen implements UntrustedContentScreen {

    static final String SOURCE = "filing";

    private final ComplianceFacade compliance;

    FacadeUntrustedContentScreen(ComplianceFacade compliance) {
        this.compliance = compliance;
    }

    @Override
    public Screening screen(UUID sectionId, String text) {
        ScreeningView result = compliance.screenUntrustedContent(sectionId, SOURCE, text);
        return new Screening(result.suspected(), result.matchedRules());
    }
}
