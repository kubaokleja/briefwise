-- Exports replayable answers from ai_generation_log as JSONL fixtures (ADR 0015, DESIGN 13.6). One object per key:
-- the newest usable answer (rows with a replay_key are usable by construction). Only fields the fixture store reads
-- (key, model, response, recorded_at) matter; feature, prompt_version and subject_id are for humans.
-- Export only rows recorded from synthetic input (synthetic_news.sql): Finnhub text must not be committed.
-- news-classification: the subject is a synthetic item (a0000000-...). news-summary: the subject is a seeded
-- portfolio (b0000000-..., V5) and every <news_item id="..."> in the logged prompt is a synthetic item.
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
      AND (subject_id::text LIKE 'a0000000-0000-4000-8000-%'      -- a synthetic item
           OR subject_id::text LIKE 'b0000000-0000-4000-8000-%')    -- or a seeded portfolio (summary)
      AND NOT EXISTS (                                             -- that cites only synthetic items
          SELECT 1 FROM regexp_matches(prompt, '<news_item id="([0-9a-f-]{36})"', 'g') AS cited(id)
          WHERE cited.id[1] NOT LIKE 'a0000000-0000-4000-8000-%')
    ORDER BY replay_key, created_at DESC
) newest
ORDER BY subject_id, replay_key;
