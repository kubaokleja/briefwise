package com.briefwise.news.infrastructure.ai;

import com.anthropic.models.messages.OutputConfig;
import java.util.function.Supplier;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the LLM classifier, active only when a chat model is configured ({@code spring.ai.model.chat}). The
 * per-call options are the only provider-specific part: Anthropic gets {@code effort}, every provider gets an
 * explicit {@code maxTokens}. Everything else (model, timeout, retries, caching, Ollama temperature) is in
 * {@code application.yml}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(LlmNewsClassifier.CHAT_MODEL_CONFIGURED)
class NewsClassificationConfig {

    @Bean
    NewsClassificationPrompt newsClassificationPrompt(NewsClassificationProperties properties) {
        return NewsClassificationPrompt.fromClasspath(properties.maxSummaryChars());
    }

    @Bean
    Supplier<ChatOptions.Builder<?>> newsClassificationCallOptions(ChatModel chatModel,
                                                                    NewsClassificationProperties properties) {
        if (chatModel instanceof AnthropicChatModel) {
            OutputConfig.Effort effort = OutputConfig.Effort.of(properties.effort());
            return () -> AnthropicChatOptions.builder().maxTokens(properties.maxTokens()).effort(effort);
        }
        return () -> ChatOptions.builder().maxTokens(properties.maxTokens());
    }
}
