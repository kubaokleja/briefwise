-- Context: news. Transactional outbox for Kafka news.raw (DESIGN 7.1, ADR 0011): a row is inserted in the same
-- transaction as its news item; the polling relay sends it and sets published_at after the broker ack.
-- No FK to news_items: the relay only needs the message, and the consumer reports an unknown item itself.

CREATE TABLE news_outbox (
    id             UUID PRIMARY KEY,               -- also the message id carried in the payload
    aggregate_id   UUID NOT NULL,                  -- the news item
    topic          VARCHAR(100) NOT NULL,
    message_key    VARCHAR(100) NOT NULL,          -- Kafka key: the ticker the item was first fetched for (K2)
    payload        JSONB NOT NULL,
    correlation_id VARCHAR(64),                    -- X-Correlation-ID of the run that stored the item, sent as a header
    attempts       INT NOT NULL DEFAULT 0,         -- failed send attempts
    last_error     TEXT,
    published_at   TIMESTAMPTZ,                    -- NULL = pending
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Serves the relay: oldest pending rows first. Published rows are kept until the M6 retention task.
CREATE INDEX news_outbox_pending_idx ON news_outbox (created_at) WHERE published_at IS NULL;
