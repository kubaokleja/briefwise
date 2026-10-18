# 0005. Resilience: Spring core retry first, Resilience4j later

- Status: Accepted
- Date: 2026-10-18

## Context

Outbound calls (LLM API, SEC EDGAR, news source, embeddings) fail transiently. We need retries with backoff and,
for LLM calls, a clear fallback ([CLAUDE.md](../../CLAUDE.md): invalid structure -> retry -> fallback with a
flag). The runtime is a short-lived batch plus a single-user local app
([ADR 0003](0003-runtime-github-actions-and-neon.md)), so there is little traffic to protect a downstream from.

## Options

1. **Spring Framework core retry** (`@Retryable` / `RetryTemplate`, in core since Spring Framework 7): no extra
   dependency, annotation or programmatic, backoff and jitter. No circuit breaker, bulkhead or rate limiter.
2. **Resilience4j**: retry, circuit breaker, rate limiter, bulkhead, metrics. Extra dependency and configuration;
   its value is highest under sustained load, which we do not have.
3. **Hand-written retry loops**: no dependency, but duplicated and easy to get wrong. Rejected.

## Decision

Option 1. Retries live in infrastructure adapters (never in domain). Cost safety does not rely on retries:
the budget and duplicate gates run before every LLM call (`aiops`), and each retry attempt is itself logged and
counted against the budget. Exact API (annotations vs `RetryTemplate`, enabling via `@EnableResilientMethods`)
to be verified in the Spring Framework 7 docs when the first adapter is written (M1).

## Consequences

- No new dependency now.
- No circuit breaker: a dead provider costs bounded retries per item, then the item falls back or is skipped.
- Revisit (new ADR, supersede this) if we add rate limiting for SEC EDGAR/LLM beyond simple backoff, or a
  long-running service with real traffic.
