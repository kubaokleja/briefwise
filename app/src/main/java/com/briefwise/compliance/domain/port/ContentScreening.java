package com.briefwise.compliance.domain.port;

import com.briefwise.compliance.domain.model.ScreeningResult;
import java.util.UUID;

/** Inbound port: screen untrusted text before it goes into a prompt. Implemented by a package-private service. */
public interface ContentScreening {

    /** Publishes {@code InjectionSuspected} when a rule matches. */
    ScreeningResult screen(UUID contentId, String source, String text);
}
