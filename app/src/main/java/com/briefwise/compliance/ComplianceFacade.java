package com.briefwise.compliance;

import com.briefwise.compliance.domain.model.ScreeningResult;
import com.briefwise.compliance.domain.port.ContentScreening;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code compliance}. Planned next: checkText (M1 slice 7 / M4).
 * Methods are added with the milestone that implements them (DESIGN 6.2); no logic here, delegate only.
 */
@Component
public class ComplianceFacade {

    private final ContentScreening screening;

    ComplianceFacade(ContentScreening screening) {
        this.screening = screening;
    }

    /**
     * Screens third-party text (news, filings) before it goes into a prompt (ADR 0013). If {@code suspected}, the
     * caller must not send it to an LLM; an {@code InjectionSuspected} event has been published.
     *
     * @param contentId the object the text belongs to, e.g. the news item id
     * @param source    the kind of content, e.g. {@code news}
     */
    public ScreeningView screenUntrustedContent(UUID contentId, String source, String text) {
        ScreeningResult result = screening.screen(contentId, source, text);
        return new ScreeningView(result.suspected(), result.matchedRules());
    }

    public record ScreeningView(boolean suspected, List<String> matchedRules) {}
}
