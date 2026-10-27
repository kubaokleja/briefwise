-- Exports replayable answers from ai_generation_log as JSONL fixtures (ADR 0015, DESIGN 13.6). One object per key:
-- the newest usable answer (rows with a replay_key are usable by construction). Only fields the fixture store reads
-- (key, model, response, recorded_at) matter; feature, prompt_version and subject_id are for humans.
-- Export only rows recorded from synthetic input (synthetic_news.sql): Finnhub text must not be committed.
--
-- Run (local Docker Compose), -A -t: unaligned, tuples only, so each line is one raw JSON object:
--   docker compose exec -T postgres psql -U briefwise -d briefwise -At -v feature=news-classification \
--     < evals/replay/export_replay.sql > evals/replay/news-classification.jsonl

SELECT json_build_object(
           'key', replay_key,
           'feature', feature,
           'model', model,
           'prompt_version', prompt_version,
           'subject_id', subject_id,
           'recorded_at', to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'response', response)::text
FROM (
    SELECT DISTINCT ON (replay_key) *
    FROM ai_generation_log
    WHERE replay_key IS NOT NULL
      AND feature = :'feature'
      AND subject_id::text LIKE 'a0000000-0000-4000-8000-%'   -- synthetic items only
    ORDER BY replay_key, created_at DESC
) newest
ORDER BY subject_id, replay_key;
