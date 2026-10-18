# 0006. Facade view records, public surface and cross-context conventions

- Status: Accepted
- Date: 2026-10-18

## Context

Session 1 ([PROGRESS](../PROGRESS.md)) made four small conventions while building `portfolio` and `market`.
They shape every later context and the ArchUnit rules, so they are recorded here. Related: CLAUDE.md
"Architecture rules", [DESIGN 6.2](../DESIGN.md#62-bounded-contexts).

## Options and decisions

1. **What other contexts may import.**
   a) facade only, with nested view records (`PortfolioFacade.PortfolioView`); b) facade + domain model;
   c) shared read models in `shared`.
   **Chosen a).** Strict reading of "the facade is the ONLY class". Cost: view records duplicate domain
   records (logged as a Domain Question; revisit when `risk`/`brief` consume them).
2. **Public classes inside a context (ArchUnit rule 4).** Facade, `domain.port`, `domain.model`, `domain.event`
   may be public top-level classes; everything else (services, adapters, entities) is package-private or
   public only where Spring requires it. Domain types must be public for adapters in `infrastructure`;
   rule 2 keeps other contexts from importing them.
3. **Cross-context references.** Plain values, no foreign keys across contexts (`positions.ticker` is a
   symbol, not an FK to `instruments`). Keeps contexts separable and allows a context to be split out.
4. **Daily prices** store `close` and `volume` only until a need for OHLC/adjusted close exists
   (see [ADR 0004](0004-market-price-source-yfinance-snapshot.md)).

## Consequences

- Consumers depend on a small, stable surface; internals can change freely.
- Mapping code between domain and view records is boilerplate we accept.
- Weakening any of these rules requires a new ADR.
