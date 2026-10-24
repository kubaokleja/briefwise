package com.briefwise.news.infrastructure.messaging;

import com.briefwise.news.NewsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the outbox relay every {@code briefwise.news.outbox.poll-interval} while
 * {@code briefwise.news.outbox.relay-enabled=true}. Not a business job (it moves already stored rows), so it is not
 * gated by {@code briefwise.execution.mode}; the job runner (M1 slice 6) calls {@link NewsFacade#publishPending()}
 * itself. Off in {@code test}, where tests call the facade.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "briefwise.news.outbox", name = "relay-enabled", havingValue = "true")
class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final NewsFacade news;

    OutboxRelayScheduler(NewsFacade news) {
        this.news = news;
    }

    @Scheduled(fixedDelayString = "${briefwise.news.outbox.poll-interval:5s}",
            initialDelayString = "${briefwise.news.outbox.poll-interval:5s}")
    void relay() {
        try {
            news.publishPending();
        } catch (RuntimeException e) {
            log.warn("Outbox relay run failed: {}", e.getMessage());
        }
    }
}
