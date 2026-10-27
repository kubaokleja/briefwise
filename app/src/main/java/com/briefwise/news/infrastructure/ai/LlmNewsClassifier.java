package com.briefwise.news.infrastructure.ai;

import com.briefwise.aiops.AiopsFacade;
import com.briefwise.aiops.AiopsFacade.GenerationEntry;
import com.briefwise.aiops.AiopsFacade.ReplayRequest;
import com.briefwise.news.domain.model.ClassifierResult;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.UnclassifiedReason;
import com.briefwise.news.domain.port.NewsClassifier;
import com.briefwise.shared.domain.exception.ReplayMissException;
import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import com.briefwise.shared.domain.model.TokenCount;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Classifies one news item with the configured chat model (ADR 0012). Per attempt: budget gate, one call with native
 * structured output, generation log, usage. The schema comes from {@link ClassificationAnswer} via
 * {@link BeanOutputConverter}; the response is parsed here rather than by {@code ChatClient.entity()}, so a malformed
 * answer is still logged and counted. Invalid output gets a re-ask up to {@code max-attempts}; a provider error
 * (already retried by the SDK) ends the item at once. Neither throws: both become {@link ClassifierResult.Failed}.
 * Every call carries the replay advisor (ADR 0015): a replayed answer is parsed the same way but neither logged nor
 * billed, and a replay miss with {@code on-miss: fail} defers the item.
 */
@Component
@ConditionalOnExpression(LlmNewsClassifier.CHAT_MODEL_CONFIGURED)
class LlmNewsClassifier implements NewsClassifier {

    static final String CHAT_MODEL_CONFIGURED = "'${spring.ai.model.chat:none}' != 'none'";

    private static final Logger log = LoggerFactory.getLogger(LlmNewsClassifier.class);

    /** Between system prompt and data block in the logged prompt text. */
    static final String LOG_SEPARATOR = "\n\n---\n\n";

    private final ChatClient chatClient;
    private final String defaultModel;
    private final AiopsFacade aiops;
    private final NewsClassificationPrompt prompt;
    private final NewsClassificationProperties properties;
    private final Supplier<ChatOptions.Builder<?>> callOptions;
    private final BeanOutputConverter<ClassificationAnswer> converter;

    LlmNewsClassifier(ChatClient.Builder chatClientBuilder, ChatModel chatModel, AiopsFacade aiops,
                      NewsClassificationPrompt prompt, NewsClassificationProperties properties,
                      Supplier<ChatOptions.Builder<?>> newsClassificationCallOptions) {
        this.chatClient = chatClientBuilder.build();
        this.defaultModel = chatModel.getOptions().getModel();
        this.aiops = aiops;
        this.prompt = prompt;
        this.properties = properties;
        this.callOptions = newsClassificationCallOptions;
        // Structured outputs do not guarantee enum casing (Claude docs), so "Positive" must still parse.
        this.converter = new BeanOutputConverter<>(ClassificationAnswer.class,
                JsonMapper.builder().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS).build());
    }

    @Override
    public ClassifierResult classify(NewsItem item) {
        String data = prompt.render(item);
        ReplayRequest replay = new ReplayRequest(properties.feature(), defaultModel, prompt.version(), data);
        UnclassifiedReason reason = UnclassifiedReason.INVALID_OUTPUT;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                aiops.ensureBudgetAvailable(properties.feature());
            } catch (UsageLimitExceededException e) {
                return new ClassifierResult.Deferred("daily budget for " + properties.feature() + " is used up");
            }
            boolean lastAttempt = attempt == properties.maxAttempts();
            Attempt result;
            try {
                result = call(item, data, replay);
            } catch (ReplayMissException e) {
                log.info("No replay entry for news item {}; it stays RECEIVED", item.id());
                return new ClassifierResult.Deferred("no replay entry");
            }
            if (result.replayed()) {
                return replayedResult(item, result);
            }
            if (result.classification().isPresent()) {
                record(item, data, replay, result, false);
                return new ClassifierResult.Classified(result.classification().get());
            }
            reason = result.failure();
            boolean giveUp = lastAttempt || reason == UnclassifiedReason.PROVIDER_ERROR;
            record(item, data, replay, result, giveUp);
            if (giveUp) {
                break;
            }
            log.info("Invalid classification for news item {} (attempt {}); asking again", item.id(), attempt);
        }
        return new ClassifierResult.Failed(reason);
    }

    /**
     * Only usable answers are recorded, so an unusable replay means the parser no longer accepts an old recording
     * (e.g. {@link ClassificationAnswer} changed without a prompt version bump). Asking again would replay the same
     * text, so the item fails at once and the warning names the cause.
     */
    private ClassifierResult replayedResult(NewsItem item, Attempt result) {
        if (result.classification().isPresent()) {
            return new ClassifierResult.Classified(result.classification().get());
        }
        log.warn("Replayed classification for news item {} is no longer usable; re-record the fixture", item.id());
        return new ClassifierResult.Failed(UnclassifiedReason.INVALID_OUTPUT);
    }

    private Attempt call(NewsItem item, String data, ReplayRequest replay) {
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
            log.warn("Classification call for news item {} failed: {}", item.id(), e.toString());
            return Attempt.failed(UnclassifiedReason.PROVIDER_ERROR, Optional.empty(), Optional.empty(),
                    new TokenCount(0, 0), elapsedSince(start), false);
        }
        Duration latency = elapsedSince(start);
        boolean replayed = aiops.isReplayed(response);
        Optional<String> model = Optional.ofNullable(response).map(r -> r.getMetadata().getModel())
                .filter(m -> !m.isBlank());
        TokenCount tokens = tokensOf(response);
        Optional<String> text = Optional.ofNullable(response).map(ChatResponse::getResult)
                .map(g -> g.getOutput().getText());
        if (text.isEmpty() || text.get().isBlank()) {
            return Attempt.failed(UnclassifiedReason.INVALID_OUTPUT, model, text, tokens, latency, replayed);
        }
        try {
            NewsClassification classification = converter.convert(text.get())
                    .toClassification(item, prompt.version());
            return new Attempt(Optional.of(classification), UnclassifiedReason.INVALID_OUTPUT, model, text, tokens,
                    latency, replayed);
        } catch (RuntimeException e) {
            log.warn("Unusable classification for news item {}: {}", item.id(), e.getMessage());
            return Attempt.failed(UnclassifiedReason.INVALID_OUTPUT, model, text, tokens, latency, replayed);
        }
    }

    /**
     * Every live attempt is logged and counted, including malformed answers (they cost tokens too). Only a usable
     * answer carries the replay request, so replay never returns a malformed one. The key is built from the same
     * request as the lookup (configured model name), not from the model ID the provider reports back.
     */
    private void record(NewsItem item, String data, ReplayRequest replay, Attempt attempt, boolean fallback) {
        String model = attempt.model().orElse(defaultModel);
        Optional<ReplayRequest> replayable = attempt.classification().isPresent() ? Optional.of(replay)
                : Optional.empty();
        aiops.recordGeneration(new GenerationEntry(properties.feature(), model, prompt.version(),
                prompt.system() + LOG_SEPARATOR + data, attempt.response(), attempt.tokens(), attempt.latency(),
                fallback, Optional.of(item.id()), replayable));
        aiops.checkAndRecordUsage(properties.feature(), model, attempt.tokens());
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

    private record Attempt(Optional<NewsClassification> classification, UnclassifiedReason failure,
                           Optional<String> model, Optional<String> response, TokenCount tokens, Duration latency,
                           boolean replayed) {

        static Attempt failed(UnclassifiedReason reason, Optional<String> model, Optional<String> response,
                              TokenCount tokens, Duration latency, boolean replayed) {
            return new Attempt(Optional.empty(), reason, model, response, tokens, latency, replayed);
        }
    }
}
