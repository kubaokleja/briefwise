package com.briefwise.news.infrastructure.compliance;

import com.briefwise.compliance.ComplianceFacade;
import com.briefwise.compliance.ComplianceFacade.PassageView;
import com.briefwise.compliance.ComplianceFacade.TextCheckRequest;
import com.briefwise.news.domain.model.SummaryPoint;
import com.briefwise.news.domain.model.SummaryViolation;
import com.briefwise.news.domain.port.SummaryCompliance;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Checks a summary as it is shown: each point is a passage, the full text is the points plus the disclaimer. */
@Component
class FacadeSummaryCompliance implements SummaryCompliance {

    private final ComplianceFacade compliance;

    FacadeSummaryCompliance(ComplianceFacade compliance) {
        this.compliance = compliance;
    }

    @Override
    public String disclaimer() {
        return compliance.disclaimer();
    }

    @Override
    public List<SummaryViolation> check(List<SummaryPoint> points, String disclaimer) {
        String fullText = points.stream().map(SummaryPoint::text).collect(Collectors.joining("\n"))
                + "\n\n" + disclaimer;
        return compliance.checkText(new TextCheckRequest(
                        points.stream().map(p -> new PassageView(p.text(), p.citations().size())).toList(), fullText))
                .violations().stream()
                .map(v -> new SummaryViolation(v.rule(), v.offendingText()))
                .toList();
    }
}
