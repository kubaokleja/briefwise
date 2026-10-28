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

/** Wiring for the LLM summarizer; same shape as {@link NewsClassificationConfig}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(LlmNewsClassifier.CHAT_MODEL_CONFIGURED)
class NewsSummaryConfig {

    @Bean
    NewsSummaryPrompt newsSummaryPrompt(NewsSummaryProperties properties) {
        return NewsSummaryPrompt.fromClasspath(properties.maxSummaryChars());
    }

    @Bean
    Supplier<ChatOptions.Builder<?>> newsSummaryCallOptions(ChatModel chatModel, NewsSummaryProperties properties) {
        if (chatModel instanceof AnthropicChatModel) {
            OutputConfig.Effort effort = OutputConfig.Effort.of(properties.effort());
            return () -> AnthropicChatOptions.builder().maxTokens(properties.maxTokens()).effort(effort);
        }
        return () -> ChatOptions.builder().maxTokens(properties.maxTokens());
    }
}
