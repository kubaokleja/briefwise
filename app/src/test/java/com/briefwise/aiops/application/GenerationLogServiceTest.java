package com.briefwise.aiops.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.briefwise.aiops.domain.model.ModelPrice;
import com.briefwise.aiops.domain.model.NewGeneration;
import com.briefwise.aiops.domain.model.PriceList;
import com.briefwise.aiops.domain.model.UnpricedModelException;
import com.briefwise.aiops.domain.port.GenerationRepository;
import com.briefwise.shared.domain.model.TokenCount;
import com.briefwise.shared.infrastructure.web.CorrelationIdFilter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class GenerationLogServiceTest {

    private final GenerationRepository repository = mock(GenerationRepository.class);
    private final GenerationLogService service = new GenerationLogService(repository,
            new PriceList(Map.of("claude-haiku-5-5", new ModelPrice(new BigDecimal("0.10"), new BigDecimal("0.50")))));

    private static NewGeneration generation(String model) {
        return new NewGeneration("news-classification", model, "v1", "prompt", Optional.of("{}"),
                new TokenCount(1_200, 200), Duration.ofMillis(850), false);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void stores_the_call_with_its_cost_and_the_correlation_id_from_the_mdc() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-1");
        UUID id = UUID.randomUUID();
        NewGeneration generation = generation("claude-haiku-5-5");
        when(repository.save(any(), any(), any())).thenReturn(id);

        assertThat(service.record(generation)).isEqualTo(id);
        verify(repository).save(generation, new BigDecimal("0.00022000"), Optional.of("corr-1"));
    }

    @Test
    void no_correlation_id_outside_a_request_or_job() {
        NewGeneration generation = generation("claude-haiku-5-5");

        service.record(generation);

        verify(repository).save(generation, new BigDecimal("0.00022000"), Optional.empty());
    }

    @Test
    void unpriced_model_is_not_stored() {
        assertThatThrownBy(() -> service.record(generation("other-model")))
                .isInstanceOf(UnpricedModelException.class);
        verifyNoInteractions(repository);
    }
}
