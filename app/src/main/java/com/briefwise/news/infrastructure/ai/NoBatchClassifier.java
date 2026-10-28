package com.briefwise.news.infrastructure.ai;

import com.briefwise.news.domain.model.BatchOutcome;
import com.briefwise.news.domain.model.GoldenCase;
import com.briefwise.news.domain.port.BatchClassifier;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Active unless the chat model is Anthropic: the eval measures the production model through the Batch API, and never
 * runs on Ollama (DESIGN 13), so it refuses instead of measuring something else.
 */
@Component
@ConditionalOnExpression("'${spring.ai.model.chat:none}' != 'anthropic'")
class NoBatchClassifier implements BatchClassifier {

    @Override
    public BatchOutcome classify(List<GoldenCase> cases) {
        return new BatchOutcome.Refused("the classification eval needs spring.ai.model.chat=anthropic");
    }
}
