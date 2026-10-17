package com.briefwise.shared.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed {@code briefwise.*} configuration (DESIGN 13.2). Configuration only: nothing reads
 * {@code jobs} or {@code llm} yet; the news, aiops and jobs contexts will.
 */
@Validated
@ConfigurationProperties("briefwise")
public record BriefwiseProperties(
        @NotNull @Valid Execution execution,
        Map<String, JobProperties> jobs,
        @NotNull @Valid Llm llm,
        @NotNull @Valid Cors cors) {

    public BriefwiseProperties {
        jobs = jobs == null ? Map.of() : Map.copyOf(jobs);
        cors = cors == null ? new Cors(List.of("http://localhost:5173")) : cors;
    }

    /** {@code on-demand}: only the admin endpoint and the job runner. {@code scheduled}: also the in-app scheduler. */
    public enum ExecutionMode { ON_DEMAND, SCHEDULED }

    /**
     * Which {@code ChatModel} backs LLM calls. {@code REPLAY} serves recorded responses from
     * {@code ai_generation_log}, so CI and the demo cost nothing and are deterministic.
     */
    public enum LlmProvider { OLLAMA, ANTHROPIC, REPLAY }

    /** What the replay provider does when a prompt has no recording: fail (CI, demo) or fall back (local). */
    public enum ReplayOnMiss { FAIL, FALLBACK }

    public record Execution(@NotNull ExecutionMode mode) {}

    public record JobProperties(boolean enabled, String cron) {}

    public record Llm(@NotNull LlmProvider provider, @NotNull @Valid Replay replay) {
        public Llm {
            replay = replay == null ? new Replay(ReplayOnMiss.FAIL) : replay;
        }
    }

    public record Replay(@NotNull ReplayOnMiss onMiss) {}

    public record Cors(@NotNull List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }
}
