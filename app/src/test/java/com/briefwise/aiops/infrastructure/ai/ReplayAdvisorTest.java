package com.briefwise.aiops.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.briefwise.aiops.domain.model.ReplayEntry;
import com.briefwise.aiops.domain.model.ReplayKey;
import com.briefwise.aiops.domain.model.ReplayPolicy;
import com.briefwise.aiops.domain.port.Replays;
import com.briefwise.shared.domain.exception.ReplayMissException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

class ReplayAdvisorTest {

    private static final ReplayKey KEY = ReplayKey.of("news-classification", "claude-haiku-5-5", "v1", "input");

    private final Replays replays = mock(Replays.class);
    private final CallAdvisorChain chain = mock(CallAdvisorChain.class);
    private final ReplayAdvisor advisor = new ReplayAdvisor(replays);
    private final ChatClientResponse live = ChatClientResponse.builder().build();

    private static ChatClientRequest request(Map<String, Object> context) {
        return ChatClientRequest.builder().prompt(new Prompt("input")).context(context).build();
    }

    private static ChatClientRequest keyed() {
        return request(Map.of(ReplayKey.CONTEXT_PARAM, KEY, ReplayKey.FEATURE_PARAM, "news-classification"));
    }

    private void policy(boolean enabled, ReplayPolicy.OnMiss onMiss) {
        when(replays.policy()).thenReturn(new ReplayPolicy(enabled, onMiss));
        when(chain.nextCall(any())).thenReturn(live);
    }

    @Test
    void without_the_replay_provider_every_call_goes_to_the_model_without_a_lookup() {
        policy(false, ReplayPolicy.OnMiss.FAIL);

        assertThat(advisor.adviseCall(keyed(), chain)).isSameAs(live);
        verify(replays, never()).find(any());
    }

    @Test
    void a_call_without_a_key_is_passed_on() {
        policy(true, ReplayPolicy.OnMiss.FAIL);

        assertThat(advisor.adviseCall(request(Map.of()), chain)).isSameAs(live);
    }

    @Test
    void hit_returns_the_recorded_text_marked_as_replayed_without_calling_the_model() {
        policy(true, ReplayPolicy.OnMiss.FAIL);
        when(replays.find(KEY)).thenReturn(Optional.of(new ReplayEntry(KEY, "claude-haiku-5-5",
                "{\"sentiment\":\"POSITIVE\"}", Instant.EPOCH)));

        ChatResponse response = advisor.adviseCall(keyed(), chain).chatResponse();

        assertThat(response.getResult().getOutput().getText()).isEqualTo("{\"sentiment\":\"POSITIVE\"}");
        assertThat(response.getMetadata().getModel()).isEqualTo("claude-haiku-5-5");
        assertThat((Boolean) response.getMetadata().get(ReplayKey.REPLAYED_METADATA)).isTrue();
        verify(chain, never()).nextCall(any());
    }

    @Test
    void miss_with_fail_throws_and_never_calls_the_model() {
        policy(true, ReplayPolicy.OnMiss.FAIL);
        when(replays.find(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> advisor.adviseCall(keyed(), chain)).isInstanceOf(ReplayMissException.class)
                .hasMessageContaining("news-classification").hasMessageContaining(KEY.hash());
        verify(chain, never()).nextCall(any());
    }

    @Test
    void miss_with_fallback_calls_the_model() {
        policy(true, ReplayPolicy.OnMiss.FALLBACK);
        when(replays.find(KEY)).thenReturn(Optional.empty());

        assertThat(advisor.adviseCall(keyed(), chain)).isSameAs(live);
    }

    @Test
    void runs_after_other_advisors_and_just_before_the_model_call() {
        assertThat(advisor.getOrder()).isLessThan(Ordered.LOWEST_PRECEDENCE).isGreaterThan(0);
    }
}
