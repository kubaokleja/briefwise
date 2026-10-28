package com.briefwise.compliance.domain.port;

import com.briefwise.compliance.domain.model.ComplianceResult;
import com.briefwise.compliance.domain.model.Passage;
import java.util.List;

/** Inbound port: check a generated text against the compliance rules. Implemented by a package-private service. */
public interface TextCheck {

    /**
     * @param passages the factual passages with their citation counts
     * @param fullText the whole text as shown, including the disclaimer
     */
    ComplianceResult check(List<Passage> passages, String fullText);

    /** The fixed disclaimer every text must contain. */
    String disclaimer();
}
