package com.briefwise.aiops.infrastructure.ai;

import com.briefwise.aiops.domain.model.ReplayEntry;
import com.briefwise.aiops.domain.model.ReplayKey;
import com.briefwise.aiops.domain.model.ReplayPolicy;
import com.briefwise.aiops.domain.port.Replays;
import com.briefwise.shared.domain.exception.ReplayMissException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * The {@code replay} provider (ADR 0015) as a {@code ChatClient} advisor: middleware in the call chain that may answer
 * instead of the model. It runs last before the model call ({@code ChatModelCallAdvisor} is
 * {@code LOWEST_PRECEDENCE}), so it replaces exactly the provider round trip. The caller puts the {@link ReplayKey} and
 * the feature into the advisor context ({@code AiopsFacade.replay}). A hit returns the recorded text with
 * {@link ReplayKey#REPLAYED_METADATA} set and no usage; a miss throws {@link ReplayMissException} ({@code fail}) or
 * goes on to the model ({@code fallback}). Without the replay provider, or without a key, it only passes the call on.
 */
@Component("replayAdvisor")
class ReplayAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ReplayAdvisor.class);

    static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    private final Replays replays;

    ReplayAdvisor(Replays replays) {
        this.replays = replays;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ReplayPolicy policy = replays.policy();
        if (!policy.enabled() || !(request.context().get(ReplayKey.CONTEXT_PARAM) instanceof ReplayKey key)) {
            return chain.nextCall(request);
        }
        String feature = String.valueOf(request.context().get(ReplayKey.FEATURE_PARAM));
        Optional<ReplayEntry> hit = replays.find(key);
        if (hit.isPresent()) {
            log.info("Replayed {} answer recorded {} (key {})", feature, hit.get().recordedAt(), key);
            return ChatClientResponse.builder().chatResponse(toChatResponse(hit.get()))
                    .context(request.context()).build();
        }
        if (policy.onMiss() == ReplayPolicy.OnMiss.FAIL) {
            throw new ReplayMissException(feature, key.hash());
        }
        log.info("No replay entry for {} (key {}); calling the model", feature, key);
        return chain.nextCall(request);
    }

    private static ChatResponse toChatResponse(ReplayEntry entry) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(entry.response()))))
                .metadata(ChatResponseMetadata.builder().model(entry.model())
                        .keyValue(ReplayKey.REPLAYED_METADATA, Boolean.TRUE).build())
                .build();
    }

    @Override
    public String getName() {
        return "ReplayAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
