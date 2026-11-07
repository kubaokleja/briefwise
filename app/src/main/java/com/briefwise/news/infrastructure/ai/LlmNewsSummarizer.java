package com.briefwise.news.infrastructure.ai;

import com.briefwise.aiops.AiopsFacade;
import com.briefwise.aiops.AiopsFacade.GenerationEntry;
import com.briefwise.aiops.AiopsFacade.ReplayRequest;
import com.briefwise.news.domain.model.SummarizerResult;
import com.briefwise.news.domain.model.SummaryDraft;
import com.briefwise.news.domain.model.SummaryRequest;
import com.briefwise.news.domain.port.NewsSummarizer;
import com.briefwise.shared.domain.exception.ReplayMissException;
import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import com.briefwise.shared.domain.model.TokenCount;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Summarises the selected news of one portfolio with the configured chat model (ADR 0017); same pattern as
 * {@link LlmNewsClassifier} (llm-adapter skill). Per attempt: budget gate, one call with native structured output
 * ({@link SummaryAnswer}) and the replay advisor, generation log ({@code subjectId} = portfolio id), usage. Invalid
 * output (including an id that was not in the request) gets a re-ask up to {@code max-attempts}; a provider error
 * ends at once. Neither throws: both become {@link SummarizerResult.Failed}, and the service uses the template.
 */
@Component
@ConditionalOnExpression(LlmNewsClassifier.CHAT_MODEL_CONFIGURED)
class LlmNewsSummarizer implements NewsSummarizer {

    private static final Logger log = LoggerFactory.getLogger(LlmNewsSummarizer.class);

    static final String INVALID_OUTPUT = "INVALID_OUTPUT";
    static final String PROVIDER_ERROR = "PROVIDER_ERROR";

    private final ChatClient chatClient;
    private final String defaultModel;
    private final AiopsFacade aiops;
    private final NewsSummaryPrompt prompt;
    private final NewsSummaryProperties properties;
    private final Supplier<ChatOptions.Builder<?>> callOptions;
    private final BeanOutputConverter<SummaryAnswer> converter = new BeanOutputConverter<>(SummaryAnswer.class);

    LlmNewsSummarizer(ChatClient.Builder chatClientBuilder, ChatModel chatModel, AiopsFacade aiops,
                      NewsSummaryPrompt prompt, NewsSummaryProperties properties,
                      @Qualifier("newsSummaryCallOptions") Supplier<ChatOptions.Builder<?>> callOptions) {
        this.chatClient = chatClientBuilder.build();
        this.defaultModel = chatModel.getOptions().getModel();
        this.aiops = aiops;
        this.prompt = prompt;
        this.properties = properties;
        this.callOptions = callOptions;
        aiops.ensureConfigured(properties.feature(), defaultModel);
    }

    @Override
    public SummarizerResult summarize(SummaryRequest request) {
        String data = prompt.render(request);
        Set<UUID> allowedIds = request.news().stream().map(r -> r.news().id()).collect(Collectors.toSet());
        ReplayRequest replay = new ReplayRequest(properties.feature(), defaultModel, prompt.version(), data);
        String reason = INVALID_OUTPUT;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                aiops.ensureBudgetAvailable(properties.feature());
            } catch (UsageLimitExceededException e) {
                return new SummarizerResult.Deferred("daily budget for " + properties.feature() + " is used up");
            }
            boolean lastAttempt = attempt == properties.maxAttempts();
            Attempt result;
            try {
                result = call(request, data, allowedIds, replay);
            } catch (ReplayMissException e) {
                log.info("No replay entry for the summary of portfolio {}; previous summary stays",
                        request.portfolioId());
                return new SummarizerResult.Deferred("no replay entry");
            }
            if (result.replayed()) {
                return replayedResult(request, result);
            }
            if (result.draft().isPresent()) {
                record(request, data, replay, result, false);
                return new SummarizerResult.Drafted(result.draft().get());
            }
            reason = result.failure();
            boolean giveUp = lastAttempt || PROVIDER_ERROR.equals(reason);
            record(request, data, replay, result, giveUp);
            if (giveUp) {
                break;
            }
            log.info("Invalid summary for portfolio {} (attempt {}); asking again", request.portfolioId(), attempt);
        }
        return new SummarizerResult.Failed(reason);
    }

    /** Only usable answers are recorded, so an unusable replay means the parser changed: fail, never re-ask. */
    private SummarizerResult replayedResult(SummaryRequest request, Attempt result) {
        if (result.draft().isPresent()) {
            return new SummarizerResult.Drafted(result.draft().get());
        }
        log.warn("Replayed summary for portfolio {} is no longer usable; re-record the fixture",
                request.portfolioId());
        return new SummarizerResult.Failed(INVALID_OUTPUT);
    }

    private Attempt call(SummaryRequest request, String data, Set<UUID> allowedIds, ReplayRequest replay) {
        long start = System.nanoTime();
        ChatResponse response;
        try {
            response = chatClient.prompt()
                    .system(prompt.system())
                    .user(data)
                    .options(callOptions.get())
                    .advisors(a -> a
                            .param(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), true)
                            .param(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey(), converter.getJsonSchema())
                            .param(ChatClientAttributes.OUTPUT_FORMAT.getKey(), converter.getFormat()))
                    .advisors(aiops.replay(replay))
                    .call()
                    .chatResponse();
        } catch (ReplayMissException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Summary call for portfolio {} failed: {}", request.portfolioId(), e.toString());
            return Attempt.failed(PROVIDER_ERROR, Optional.empty(), Optional.empty(), new TokenCount(0, 0),
                    elapsedSince(start), false);
        }
        Duration latency = elapsedSince(start);
        boolean replayed = aiops.isReplayed(response);
        Optional<String> model = Optional.ofNullable(response).map(r -> r.getMetadata().getModel())
                .filter(m -> !m.isBlank());
        TokenCount tokens = tokensOf(response);
        Optional<String> text = Optional.ofNullable(response).map(ChatResponse::getResult)
                .map(g -> g.getOutput().getText());
        if (text.isEmpty() || text.get().isBlank()) {
            return Attempt.failed(INVALID_OUTPUT, model, text, tokens, latency, replayed);
        }
        try {
            SummaryDraft draft = converter.convert(text.get()).toDraft(allowedIds, properties.maxPoints(),
                    model.orElse(defaultModel), prompt.version());
            return new Attempt(Optional.of(draft), INVALID_OUTPUT, model, text, tokens, latency, replayed);
        } catch (RuntimeException e) {
            log.warn("Unusable summary for portfolio {}: {}", request.portfolioId(), e.getMessage());
            return Attempt.failed(INVALID_OUTPUT, model, text, tokens, latency, replayed);
        }
    }

    /**
     * Every live attempt is logged and counted; only a usable answer carries the replay request (ADR 0015). Priced by
     * the configured model; a failure to record a paid call is logged, never thrown (ADR 0019, as in
     * {@link LlmNewsClassifier}).
     */
    private void record(SummaryRequest request, String data, ReplayRequest replay, Attempt attempt,
                        boolean fallback) {
        Optional<ReplayRequest> replayable = attempt.draft().isPresent() ? Optional.of(replay) : Optional.empty();
        try {
            aiops.recordGeneration(new GenerationEntry(properties.feature(), defaultModel, attempt.model(),
                    prompt.version(), prompt.system() + LlmNewsClassifier.LOG_SEPARATOR + data, attempt.response(),
                    attempt.tokens(), attempt.latency(), fallback, Optional.of(request.portfolioId()), replayable));
            aiops.checkAndRecordUsage(properties.feature(), defaultModel, attempt.tokens());
        } catch (RuntimeException e) {
            log.error("Could not record the summary call for portfolio {} ({} tokens); the call stays unlogged",
                    request.portfolioId(), attempt.tokens(), e);
        }
    }

    private static TokenCount tokensOf(ChatResponse response) {
        if (response == null || response.getMetadata().getUsage() == null) {
            return new TokenCount(0, 0);
        }
        Usage usage = response.getMetadata().getUsage();
        return new TokenCount(intOf(usage.getPromptTokens()), intOf(usage.getCompletionTokens()),
                intOf(usage.getCacheWriteInputTokens()), intOf(usage.getCacheReadInputTokens()));
    }

    private static int intOf(Number n) {
        return n == null ? 0 : Math.toIntExact(n.longValue());
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private record Attempt(Optional<SummaryDraft> draft, String failure, Optional<String> model,
                           Optional<String> response, TokenCount tokens, Duration latency, boolean replayed) {

        static Attempt failed(String reason, Optional<String> model, Optional<String> response, TokenCount tokens,
                              Duration latency, boolean replayed) {
            return new Attempt(Optional.empty(), reason, model, response, tokens, latency, replayed);
        }
    }
}
