package com.briefwise.news.domain.port;

import java.util.UUID;

/** Outbound port: corrections to the LLM generation log ({@code aiops}) after a call's result was decided. */
public interface GenerationAudit {

    /**
     * The call's usable answer still ended in the fallback (a summary draft that failed the compliance check). Never
     * throws: the summary is already stored, the flag only keeps the log's fallback rate right.
     */
    void markFallback(UUID generationId);
}
