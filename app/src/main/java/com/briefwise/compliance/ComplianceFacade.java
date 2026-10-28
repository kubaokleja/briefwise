package com.briefwise.compliance;

import com.briefwise.compliance.domain.model.ComplianceResult;
import com.briefwise.compliance.domain.model.Passage;
import com.briefwise.compliance.domain.model.ScreeningResult;
import com.briefwise.compliance.domain.port.ContentScreening;
import com.briefwise.compliance.domain.port.TextCheck;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code compliance}. Methods are added with the milestone that
 * implements them (DESIGN 6.2); no logic here, delegate only.
 */
@Component
public class ComplianceFacade {

    private final ContentScreening screening;
    private final TextCheck textCheck;

    ComplianceFacade(ContentScreening screening, TextCheck textCheck) {
        this.screening = screening;
        this.textCheck = textCheck;
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

    /**
     * Checks a generated text against the compliance rules checked in code (DESIGN 11, ADR 0017): no
     * recommendations, no forecasts or promises, at least one citation per passage, the fixed disclaimer present.
     * Deterministic patterns only in M1; paraphrases can pass.
     */
    public ComplianceResultView checkText(TextCheckRequest request) {
        ComplianceResult result = textCheck.check(
                request.passages().stream().map(p -> new Passage(p.text(), p.citations())).toList(),
                request.fullText());
        return new ComplianceResultView(result.passed(), result.violations().stream()
                .map(v -> new ViolationView(v.rule().name(), v.offendingText(), v.explanation()))
                .toList());
    }

    /** The fixed disclaimer, appended in code to every AI text shown to an RM. */
    public String disclaimer() {
        return textCheck.disclaimer();
    }

    public record ScreeningView(boolean suspected, List<String> matchedRules) {}

    /**
     * @param passages the factual passages (e.g. summary points) with their citation counts
     * @param fullText the whole text as shown, including the disclaimer
     */
    public record TextCheckRequest(List<PassageView> passages, String fullText) {

        public TextCheckRequest {
            passages = List.copyOf(passages);
        }
    }

    public record PassageView(String text, int citations) {}

    /** {@code rule} is a {@code RuleId} name, e.g. {@code NO_RECOMMENDATION}. */
    public record ViolationView(String rule, String offendingText, String explanation) {}

    public record ComplianceResultView(boolean passed, List<ViolationView> violations) {}
}
