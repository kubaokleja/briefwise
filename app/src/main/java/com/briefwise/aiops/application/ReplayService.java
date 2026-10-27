package com.briefwise.aiops.application;

import com.briefwise.aiops.domain.model.ReplayEntry;
import com.briefwise.aiops.domain.model.ReplayKey;
import com.briefwise.aiops.domain.model.ReplayPolicy;
import com.briefwise.aiops.domain.port.ReplayStore;
import com.briefwise.aiops.domain.port.Replays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Asks the stores in order ({@code @Order}: committed fixtures, then the generation log); the first hit wins. */
@Service
class ReplayService implements Replays {

    private final ReplayPolicy policy;
    private final List<ReplayStore> stores;

    ReplayService(ReplayPolicy policy, List<ReplayStore> stores) {
        this.policy = policy;
        this.stores = List.copyOf(stores);
    }

    @Override
    public ReplayPolicy policy() {
        return policy;
    }

    @Override
    public Optional<ReplayEntry> find(ReplayKey key) {
        return stores.stream().map(store -> store.find(key)).flatMap(Optional::stream).findFirst();
    }
}
