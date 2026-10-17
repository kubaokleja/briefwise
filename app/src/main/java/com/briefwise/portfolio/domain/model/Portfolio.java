package com.briefwise.portfolio.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Portfolio(UUID id, ClientId clientId, String name, RiskProfile profile, List<Position> positions) {

    public Portfolio {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(profile, "profile");
        positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
    }
}
