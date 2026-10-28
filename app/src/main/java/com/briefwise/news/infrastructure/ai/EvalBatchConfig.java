package com.briefwise.news.infrastructure.ai;

import com.anthropic.client.AnthropicClient;
import java.util.Map;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Anthropic SDK client for Message Batches (ADR 0018). Spring AI's {@code ChatClient} has no batch API, so the
 * eval talks to the SDK directly, built by Spring AI's own {@link AnthropicSetup} from the same
 * {@code spring.ai.anthropic.*} connection settings (key, base URL, retries) as the chat model.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "anthropic")
class EvalBatchConfig {

    @Bean(destroyMethod = "close")
    AnthropicClient newsEvalAnthropicClient(@Value("${spring.ai.anthropic.base-url:#{null}}") String baseUrl,
                                            @Value("${spring.ai.anthropic.api-key:}") String apiKey,
                                            @Value("${spring.ai.anthropic.max-retries:2}") int maxRetries,
                                            EvalBatchProperties properties) {
        return AnthropicSetup.setupSyncClient(baseUrl, apiKey, properties.requestTimeout(), maxRetries, null,
                Map.of());
    }
}
