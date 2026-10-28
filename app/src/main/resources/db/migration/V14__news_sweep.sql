-- Context: news. Sweep of items stuck in RECEIVED (M1 slice 6, ADR 0016): DLT'd, never delivered, deferred on budget,
-- or reopened after a provider error. Each sweep attempt is counted; at the cap the item gives up as UNCLASSIFIED.

ALTER TABLE news_items
    ADD COLUMN sweep_attempts INT NOT NULL DEFAULT 0 CHECK (sweep_attempts >= 0);

ALTER TABLE news_items
    DROP CONSTRAINT news_items_unclassified_reason_check,
    ADD CONSTRAINT news_items_unclassified_reason_check CHECK (unclassified_reason IN ('INVALID_OUTPUT',
        'PROVIDER_ERROR', 'INJECTION_SUSPECTED', 'RETRIES_EXHAUSTED'));

-- Serves the sweep: oldest RECEIVED items first. Most items leave RECEIVED within seconds, so the index stays small.
CREATE INDEX news_items_received_idx ON news_items (created_at) WHERE status = 'RECEIVED';

-- Serves reopening provider errors: UNCLASSIFIED items by classification time.
CREATE INDEX news_items_provider_error_idx ON news_items (classified_at) WHERE unclassified_reason = 'PROVIDER_ERROR';
