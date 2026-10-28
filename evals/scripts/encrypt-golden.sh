#!/usr/bin/env bash
# Encrypts evals/sentiment_golden.csv -> evals/sentiment_golden.csv.gpg (AES-256, symmetric; ADR 0018).
# The clear CSV holds Finnhub text (no redistribution), so only the encrypted copy is committed.
# Passphrase from EVAL_DATA_PASSPHRASE (same value as the GitHub Secret). Run from the repo root.
set -euo pipefail
: "${EVAL_DATA_PASSPHRASE:?set EVAL_DATA_PASSPHRASE}"
gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-fd 0 \
    --symmetric --cipher-algo AES256 \
    --output evals/sentiment_golden.csv.gpg evals/sentiment_golden.csv <<< "$EVAL_DATA_PASSPHRASE"
echo "encrypted evals/sentiment_golden.csv -> evals/sentiment_golden.csv.gpg"
