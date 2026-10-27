-- Context: aiops. Replay provider (M1 slice 5, ADR 0015): the generation log doubles as a response cache.
-- replay_key = SHA-256 of feature, model, prompt version and the normalised data block (L7). Set only for usable
-- answers, so a malformed or fallback row is never replayed. Fixtures for CI and the demo are exported from here
-- to evals/replay/ (evals/replay/export_replay.sql).

ALTER TABLE ai_generation_log
    ADD COLUMN replay_key VARCHAR(64);

-- Serves the replay lookup: the newest row for a key.
CREATE INDEX ai_generation_log_replay_key_idx ON ai_generation_log (replay_key, created_at DESC)
    WHERE replay_key IS NOT NULL;
