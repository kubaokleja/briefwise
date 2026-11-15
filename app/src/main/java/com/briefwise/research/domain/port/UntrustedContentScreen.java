package com.briefwise.research.domain.port;

import com.briefwise.research.domain.model.Screening;
import java.util.UUID;

/** Outbound port: injection screening of third-party filing text at ingest (ADR 0013). */
public interface UntrustedContentScreen {

    Screening screen(UUID sectionId, String text);
}
