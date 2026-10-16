# Briefwise

AI copilot for private banking Relationship Managers. Every morning it scans client portfolios, classifies
relevant news with an LLM, forecasts volatility and VaR with a backtested quant model, answers "why is X down?"
with cited evidence from SEC filings, and drafts a compliance-checked client commentary that the RM approves
before anything leaves the bank.

*Every number has a source, nothing reaches a client without human approval.*

Built with Java 25, Spring Boot 4, Spring AI 2.0, Kafka, PostgreSQL + pgvector and a Python quant pipeline,
with evaluation suites gating every merge, running for under 5 CHF a month.

> **Work in progress.** Synthetic data only. Not financial advice.

Design and decisions: [docs/DESIGN.md](docs/DESIGN.md), [docs/decisions/](docs/decisions/).
