package com.briefwise.aiops.domain.port;

import com.briefwise.aiops.domain.model.ReplayEntry;
import com.briefwise.aiops.domain.model.ReplayKey;
import com.briefwise.aiops.domain.model.ReplayPolicy;
import java.util.Optional;

/** Inbound port: recorded answers for the {@code replay} provider (ADR 0015). */
public interface Replays {

    /** The configured policy ({@code briefwise.llm.provider}, {@code briefwise.llm.replay.on-miss}). */
    ReplayPolicy policy();

    /** The recorded answer for the key, from the first store that has one (fixtures before the generation log). */
    Optional<ReplayEntry> find(ReplayKey key);
}
