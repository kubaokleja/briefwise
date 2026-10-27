package com.briefwise.aiops.domain.port;

import com.briefwise.aiops.domain.model.ReplayEntry;
import com.briefwise.aiops.domain.model.ReplayKey;
import java.util.Optional;

/** Outbound port: one source of recorded answers (committed fixtures, the generation log). */
public interface ReplayStore {

    Optional<ReplayEntry> find(ReplayKey key);
}
