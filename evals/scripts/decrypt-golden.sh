#!/usr/bin/env bash
# Decrypts evals/sentiment_golden.csv.gpg -> evals/sentiment_golden.csv (git-ignored; ADR 0018).
# Passphrase from EVAL_DATA_PASSPHRASE (GitHub Secret in evals.yml). Run from the repo root.
set -euo pipefail
: "${EVAL_DATA_PASSPHRASE:?set EVAL_DATA_PASSPHRASE}"
gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-fd 0 \
    --decrypt --output evals/sentiment_golden.csv evals/sentiment_golden.csv.gpg <<< "$EVAL_DATA_PASSPHRASE"
echo "decrypted evals/sentiment_golden.csv.gpg -> evals/sentiment_golden.csv"
