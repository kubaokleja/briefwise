package com.briefwise.aiops.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.aiops.domain.model.ReplayEntry;
import com.briefwise.aiops.domain.model.ReplayKey;
import com.briefwise.aiops.domain.model.ReplayPolicy;
import com.briefwise.aiops.domain.port.ReplayStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReplayServiceTest {

    private final ReplayStore fixtures = mock(ReplayStore.class);
    private final ReplayStore generationLog = mock(ReplayStore.class);
    private final ReplayService service = new ReplayService(new ReplayPolicy(true, ReplayPolicy.OnMiss.FAIL),
            List.of(fixtures, generationLog));
    private final ReplayKey key = ReplayKey.of("news-classification", "claude-haiku-5-5", "v1", "input");

    @Test
    void first_store_with_an_entry_wins_and_later_stores_are_not_asked() {
        ReplayEntry fixture = new ReplayEntry(key, "claude-haiku-5-5", "{}", Instant.EPOCH);
        when(fixtures.find(key)).thenReturn(Optional.of(fixture));

        assertThat(service.find(key)).contains(fixture);
        verify(generationLog, never()).find(key);
    }

    @Test
    void falls_through_to_the_next_store_and_is_empty_when_none_has_the_key() {
        when(fixtures.find(key)).thenReturn(Optional.empty());
        when(generationLog.find(key)).thenReturn(Optional.empty());

        assertThat(service.find(key)).isEmpty();
        verify(generationLog).find(key);
    }
}
