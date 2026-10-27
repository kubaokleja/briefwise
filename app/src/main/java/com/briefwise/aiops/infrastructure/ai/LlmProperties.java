package com.briefwise.aiops.infrastructure.ai;

import com.briefwise.aiops.domain.model.ReplayPolicy;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code briefwise.llm.*} (DESIGN 13.2, ADR 0015). {@code provider=replay} answers calls from recordings; any other
 * value ({@code ollama}, {@code anthropic}) passes every call to the chat model chosen by {@code spring.ai.model.chat}.
 * {@code replay.on-miss}: {@code fail} never calls the model, {@code fallback} calls it and records the answer.
 * {@code replay.fixtures-dir}: the committed {@code *.jsonl} fixtures, relative to {@code app/}.
 */
@Validated
@ConfigurationProperties("briefwise.llm")
record LlmProperties(@DefaultValue("ollama") @NotNull Provider provider, @DefaultValue Replay replay) {

    enum Provider { OLLAMA, ANTHROPIC, REPLAY }

    record Replay(@DefaultValue("fail") @NotNull ReplayPolicy.OnMiss onMiss,
                  @DefaultValue("../evals/replay") @NotNull Path fixturesDir) {}

    ReplayPolicy policy() {
        return new ReplayPolicy(provider == Provider.REPLAY, replay.onMiss());
    }
}
