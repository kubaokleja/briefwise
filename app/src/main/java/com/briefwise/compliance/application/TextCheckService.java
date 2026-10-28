package com.briefwise.compliance.application;

import com.briefwise.compliance.domain.model.ComplianceResult;
import com.briefwise.compliance.domain.model.Disclaimer;
import com.briefwise.compliance.domain.model.Passage;
import com.briefwise.compliance.domain.model.TextRules;
import com.briefwise.compliance.domain.port.TextCheck;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class TextCheckService implements TextCheck {

    private static final Logger log = LoggerFactory.getLogger(TextCheckService.class);

    @Override
    public ComplianceResult check(List<Passage> passages, String fullText) {
        ComplianceResult result = TextRules.check(TextRules.M1, passages, fullText, Disclaimer.TEXT);
        if (!result.passed()) {
            log.info("Compliance check found {} violation(s): {}", result.violations().size(),
                    result.violations().stream().map(v -> v.rule() + " [" + v.offendingText() + "]").toList());
        }
        return result;
    }

    @Override
    public String disclaimer() {
        return Disclaimer.TEXT;
    }
}
