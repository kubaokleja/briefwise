package com.briefwise.news.infrastructure.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchResult;
import com.briefwise.aiops.AiopsFacade;
import com.briefwise.aiops.AiopsFacade.BudgetView;
import com.briefwise.aiops.AiopsFacade.GenerationEntry;
import com.briefwise.news.domain.model.BatchOutcome;
import com.briefwise.news.domain.model.EvalLabels;
import com.briefwise.news.domain.model.EvalPrediction;
import com.briefwise.news.domain.model.GoldenCase;
import com.briefwise.news.domain.model.NewsClassification;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.model.NewsStatus;
import com.briefwise.news.domain.port.BatchClassifier;
import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import com.briefwise.shared.domain.model.PricingTier;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Classifies the golden set in one Message Batch (ADR 0018): asynchronous, half price, results usually within
 * minutes. Each request is what {@link LlmNewsClassifier} sends in production: the same system prompt (cached
 * prefix), the same rendered {@code <news_item>} block, the same schema and effort (built by Spring AI's
 * {@link AnthropicChatOptions}), the same max tokens and model. Answers are parsed by the same converter and checked
 * by {@link ClassificationAnswer#toClassification}; there is no re-ask, so the eval measures first-attempt quality.
 * <p>
 * Gates before submitting: no golden headline may be in the few-shot block (leakage), and a worst-case estimate (no
 * cache hits, every answer at max tokens) must fit the eval feature's remaining daily budget. Every result is logged
 * and counted at the batch price, errored and expired ones included.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "anthropic")
class AnthropicBatchClassifier implements BatchClassifier {

    private static final Logger log = LoggerFactory.getLogger(AnthropicBatchClassifier.class);

    /** Conservative characters per token for the pre-flight estimate (English averages about 4). */
    private static final int CHARS_PER_TOKEN = 3;

    private final AnthropicClient client;
    private final String model;
    private final AiopsFacade aiops;
    private final NewsClassificationPrompt prompt;
    private final NewsClassificationProperties classification;
    private final EvalBatchProperties properties;
    private final BeanOutputConverter<ClassificationAnswer> converter;
    private final OutputConfig outputConfig;

    AnthropicBatchClassifier(@Qualifier("newsEvalAnthropicClient") AnthropicClient client, ChatModel chatModel,
                             AiopsFacade aiops, NewsClassificationPrompt prompt,
                             NewsClassificationProperties classification, EvalBatchProperties properties) {
        this.client = client;
        this.model = chatModel.getOptions().getModel();
        this.aiops = aiops;
        this.prompt = prompt;
        this.classification = classification;
        this.properties = properties;
        this.converter = new BeanOutputConverter<>(ClassificationAnswer.class,
                JsonMapper.builder().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS).build());
        this.outputConfig = AnthropicChatOptions.builder()
                .effort(OutputConfig.Effort.of(classification.effort()))
                .outputSchema(converter.getJsonSchema())
                .build()
                .getOutputConfig();
        aiops.ensureConfigured(properties.feature(), model);
    }

    @Override
    public BatchOutcome classify(List<GoldenCase> cases) {
        Optional<String> leak = leakedHeadline(cases);
        if (leak.isPresent()) {
            return new BatchOutcome.Refused("golden headline is in the few-shot examples: " + leak.get());
        }
        Map<UUID, String> data = cases.stream()
                .collect(Collectors.toMap(GoldenCase::id, c -> prompt.render(asNewsItem(c))));
        Optional<String> overBudget = budgetProblem(cases.size(), data);
        if (overBudget.isPresent()) {
            return new BatchOutcome.Refused(overBudget.get());
        }

        BatchCreateParams.Builder params = BatchCreateParams.builder();
        cases.forEach(c -> params.addRequest(request(c, data.get(c.id()))));
        MessageBatch batch = client.messages().batches().create(params.build());
        log.info("Submitted eval batch {} ({} requests, model {}, prompt {})", batch.id(), cases.size(), model,
                prompt.version());

        if (awaitEnd(batch.id(), properties.maxWait()).isEmpty()) {
            return cancelled(batch.id(), cases, data);
        }
        return collect(batch.id(), cases, data);
    }

    /**
     * A batch that outlives {@code max-wait} is cancelled: unfinished requests are then not billed, but the finished
     * ones are, so once it ends they are recorded like a completed batch (generation log and budget). The outcome stays
     * incomplete: a partial golden set is not scored.
     */
    private BatchOutcome cancelled(String batchId, List<GoldenCase> cases, Map<UUID, String> data) {
        try {
            client.messages().batches().cancel(batchId);
        } catch (RuntimeException e) {
            log.error("Could not cancel eval batch {}; cancel it in the Console: {}", batchId, e.toString());
            return new BatchOutcome.Incomplete(batchId, "not ended after " + properties.maxWait()
                    + " and the cancel failed; cancel it in the Console and fetch the results with this batch id");
        }
        log.warn("Eval batch {} cancelled after {}", batchId, properties.maxWait());
        if (awaitEnd(batchId, properties.cancelWait()).isEmpty()) {
            return new BatchOutcome.Incomplete(batchId, "cancelled after " + properties.maxWait()
                    + " but not ended after " + properties.cancelWait()
                    + "; billed results are not recorded, fetch them with this batch id");
        }
        String recordedUsd = collect(batchId, cases, data).costUsd().stripTrailingZeros().toPlainString();
        return new BatchOutcome.Incomplete(batchId, "cancelled after " + properties.maxWait()
                + "; the requests that had finished are recorded (" + recordedUsd + " USD), not scored");
    }

    private Optional<String> leakedHeadline(List<GoldenCase> cases) {
        String fewShot = prompt.system().toLowerCase(Locale.ROOT);
        return cases.stream().map(GoldenCase::headline)
                .filter(h -> fewShot.contains(h.strip().toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    /** Worst case: nothing cached, every answer uses all of {@code max-tokens}. */
    private Optional<String> budgetProblem(int requests, Map<UUID, String> data) {
        BudgetView budget;
        try {
            budget = aiops.ensureBudgetAvailable(properties.feature());
        } catch (UsageLimitExceededException e) {
            return Optional.of("daily budget for " + properties.feature() + " is used up");
        }
        long inputChars = data.values().stream().mapToLong(d -> prompt.system().length() + d.length()).sum();
        TokenCount worstCase = new TokenCount(Math.toIntExact(inputChars / CHARS_PER_TOKEN),
                Math.multiplyExact(requests, classification.maxTokens()));
        BigDecimal estimate = aiops.costOf(model, worstCase, PricingTier.BATCH);
        if (estimate.compareTo(budget.remainingUsd()) > 0) {
            return Optional.of("estimated " + estimate + " USD (worst case) exceeds the remaining "
                    + budget.remainingUsd() + " USD of " + properties.feature());
        }
        log.info("Eval batch estimate {} USD (worst case), {} USD left today", estimate, budget.remainingUsd());
        return Optional.empty();
    }

    private BatchCreateParams.Request request(GoldenCase c, String data) {
        return BatchCreateParams.Request.builder()
                .customId(c.id().toString())
                .params(BatchCreateParams.Request.Params.builder()
                        .model(model)
                        .maxTokens(classification.maxTokens())
                        .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                                .text(prompt.system())
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                        .addUserMessage(data)
                        .outputConfig(outputConfig)
                        .build())
                .build();
    }

    private Optional<MessageBatch> awaitEnd(String batchId, Duration maxWait) {
        Instant deadline = Instant.now().plus(maxWait);
        try {
            MessageBatch current = client.messages().batches().retrieve(batchId);
            while (!current.processingStatus().equals(MessageBatch.ProcessingStatus.ENDED)) {
                if (!Instant.now().isBefore(deadline)) {
                    log.warn("Eval batch {} still {} after {}", batchId, current.processingStatus().asString(),
                            maxWait);
                    return Optional.empty();
                }
                Thread.sleep(properties.pollInterval());
                current = client.messages().batches().retrieve(batchId);
            }
            return Optional.of(current);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private BatchOutcome.Completed collect(String batchId, List<GoldenCase> cases, Map<UUID, String> data) {
        Map<UUID, GoldenCase> byId = cases.stream().collect(Collectors.toMap(GoldenCase::id, Function.identity()));
        List<EvalPrediction> predictions = new ArrayList<>();
        BigDecimal cost = BigDecimal.ZERO;
        try (StreamResponse<MessageBatchIndividualResponse> results =
                     client.messages().batches().resultsStreaming(batchId)) {
            for (MessageBatchIndividualResponse response : (Iterable<MessageBatchIndividualResponse>)
                    results.stream()::iterator) {
                Optional<GoldenCase> golden = caseOf(response.customId(), byId);
                if (golden.isEmpty()) {
                    log.warn("Eval batch {} returned unknown custom id {}", batchId, response.customId());
                    continue;
                }
                EvalPrediction prediction = predictionOf(golden.get(), data.get(golden.get().id()),
                        response.result());
                predictions.add(prediction);
                cost = cost.add(aiops.costOf(model, prediction.tokens(), PricingTier.BATCH));
            }
        }
        log.info("Eval batch {} ended: {} results, {} USD", batchId, predictions.size(), cost);
        return new BatchOutcome.Completed(batchId, model, prompt.version(), predictions, cost);
    }

    private static Optional<GoldenCase> caseOf(String customId, Map<UUID, GoldenCase> byId) {
        try {
            return Optional.ofNullable(byId.get(UUID.fromString(customId)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Errored, canceled and expired requests are not billed: logged with zero tokens and no response. */
    private EvalPrediction predictionOf(GoldenCase golden, String data, MessageBatchResult result) {
        if (!result.isSucceeded()) {
            log.warn("Eval request for case {} {}", golden.id(), result.type());
            record(golden, data, Optional.empty(), Optional.empty(), new TokenCount(0, 0));
            return EvalPrediction.invalid(golden.id(), new TokenCount(0, 0));
        }
        Message message = result.asSucceeded().message();
        TokenCount tokens = tokensOf(message.usage());
        String text = message.content().stream().flatMap(block -> block.text().stream())
                .map(TextBlock::text).collect(Collectors.joining());
        record(golden, data, Optional.of(message.model().asString()), Optional.of(text), tokens);
        try {
            NewsClassification parsed = converter.convert(text).toClassification(asNewsItem(golden),
                    prompt.version());
            return new EvalPrediction(golden.id(),
                    Optional.of(new EvalLabels(parsed.sentiment(), parsed.relevance(), parsed.eventType())), tokens);
        } catch (RuntimeException e) {
            log.info("Unusable eval answer for case {}: {}", golden.id(), e.getMessage());
            return EvalPrediction.invalid(golden.id(), tokens);
        }
    }

    /**
     * Batch requests have no per-request latency; logged as zero. Not replayable: eval answers are not reused. Priced
     * by the configured model; the batch is already billed, so a failure to record is logged, never thrown (ADR 0019).
     */
    private void record(GoldenCase golden, String data, Optional<String> responseModel, Optional<String> response,
                        TokenCount tokens) {
        try {
            aiops.recordGeneration(new GenerationEntry(properties.feature(), model, responseModel, prompt.version(),
                    prompt.system() + LlmNewsClassifier.LOG_SEPARATOR + data, response, tokens, Duration.ZERO, false,
                    Optional.of(golden.id()), Optional.empty()), PricingTier.BATCH);
            aiops.checkAndRecordUsage(properties.feature(), model, tokens, PricingTier.BATCH);
        } catch (RuntimeException e) {
            log.error("Could not record the eval call for case {} ({} tokens); the call stays unlogged", golden.id(),
                    tokens, e);
        }
    }

    private static TokenCount tokensOf(Usage usage) {
        return new TokenCount(Math.toIntExact(usage.inputTokens()), Math.toIntExact(usage.outputTokens()),
                Math.toIntExact(usage.cacheCreationInputTokens().orElse(0L)),
                Math.toIntExact(usage.cacheReadInputTokens().orElse(0L)));
    }

    /** The prompt renders a {@link NewsItem}; only tickers, source, headline and summary reach the data block. */
    private static NewsItem asNewsItem(GoldenCase c) {
        return new NewsItem(c.id(), c.headline(), c.summary(), "golden:" + c.id(), Instant.EPOCH, c.source(),
                c.tickers(), NewsStatus.RECEIVED, Optional.empty());
    }
}
