package com.briefwise.news.domain.model;

/** The broker did not acknowledge a message (unreachable, timeout, rejected). The outbox row stays pending. */
public class MessagePublishException extends RuntimeException {

    public MessagePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
