package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.MessagePublishException;
import com.briefwise.news.domain.model.OutboxMessage;

/** Outbound port: sends one outbox message to the broker and waits for the acknowledgement. */
public interface NewsMessagePublisher {

    /** @throws MessagePublishException if the broker did not acknowledge the message in time */
    void publish(OutboxMessage message);
}
