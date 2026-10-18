# 0007. UI delivery strategy

- Status: Accepted
- Date: 2026-10-18

## Context

[DESIGN 15](../DESIGN.md#15-ui-design) lists the screens and a design system, but nothing said when each screen is
built. PLANNING had no UI tasks, there was no `ui/` skeleton, no frontend CI and no UI testing strategy. An audit
also found: no login screen although `access` uses JWT ([12](../DESIGN.md#12-non-functional-requirements)); no
loading/empty/error or domain states (`UNCLASSIFIED`, `isFallback`, RAG "I don't know", HTTP 429, suspected
injection); no defined scope for the "AI draft" marker (EU AI Act Art. 50); `EventSource` cannot send headers, which
conflicts with auth and the correlation ID; no contract between Java view records and TypeScript types; nothing
forbids rendering LLM output as HTML; the `demo` Compose profile and an app image do not exist yet.

Constraints: ~12 h/week in total, shared with evals, CI, Kafka and the writeup; evals are never displaced by UI work.
The owner is a Java developer learning frontend.

## Options

**Delivery order**
1. **Thin vertical slices**: each screen ships in the milestone of the backend feature it shows. The UI grows with
   the API, every feature is demoable when done, the effort is spread out. Needs a skeleton and CI from M0.
2. **UI phase at the end** (M6): backend first, UI later. Less context switching, but nothing is demoable for
   months, API design is never tested by a consumer, and the UI is the first thing squeezed by time.

**Serving the UI in `demo`**
1. **Spring Boot serves the built UI**: a multi-stage Dockerfile builds `ui/dist` in a Node stage and copies it to
   Spring's `static/`; small SPA-fallback controller. One image, one origin. No Maven frontend plugin, so
   `./mvnw verify` never needs Node.
2. **nginx container**: serves `dist` and proxies `/api`. Cleaner separation, but a second image and nginx config.

**Token storage**
1. **In memory**: immune to XSS theft of a stored token and no CSRF, but a reload means logging in again.
2. **sessionStorage**: survives reload; readable by any injected script.
3. **httpOnly cookie**: JavaScript cannot read it, survives reload; requires CSRF protection and same-origin.

**Java/TS type contract**
1. **Hand-written TS types**: no dependency; a renamed Java field fails silently at runtime.
2. **OpenAPI**: springdoc generates the spec from controllers, openapi-typescript generates types; CI catches drift.
   Two dependencies.

**Server state in the UI**
1. **Small hand-rolled `useApi` hook** (~30 lines: loading, data, error). Readable, no dependency, no caching.
2. **TanStack Query**: caching, retries, refetch, invalidation after mutations. One more library and concepts.

**Brief generation UX (< 60 s)**
1. **`202 Accepted` + polling**: simple, coarse feedback.
2. **SSE progress events**: shows each step (data, draft, compliance check, revision); more work, clearer for the RM.

## Decision

- **Thin vertical slices**, one per milestone. Screens, endpoints, tests and hours per milestone are tracked in
  [PLANNING](../PLANNING.md) under each milestone (~54 h in total: M0 8, M1 7, M2 8, M3 6, M4 12, M5 2, M6 10).
- **Nothing is planned to be cut.** Contingency only, if a milestone slips, in this order: Evals screen, light mode,
  position heatmap, brief change-history view, cost chart (numbers only), Playwright in CI (keep local), M1
  "run now" button. Never cut: AI marker, citations, compliance violations + Approve/Reject, login, loading/empty/
  error states, `ui-verify` CI.
- **Serving**: `local`/`cloud` use the Vite dev server with a proxy for `/api` and `/actuator` (same origin);
  `demo` uses option 1 (Spring serves the build), delivered with the app image in M6; `job`/`test` have no UI.
  `briefwise.cors.allowed-origins` stays for direct calls from the dev server and is empty in `demo`.
- **Auth (M4)**: JWT in an `HttpOnly; Secure; SameSite=Strict` cookie set by the login endpoint, read by Spring
  Security from the cookie; CSRF via Spring Security's cookie CSRF token echoed in `X-XSRF-TOKEN`; logout clears
  the cookie. Never a token in a query string. Until M4 the app binds to `127.0.0.1` in `local`, because the admin
  endpoint is open.
- **Streaming**: `fetch` + `ReadableStream` with our own SSE parser (not `EventSource`), so cookies, CSRF and
  `X-Correlation-ID` work and requests can be `POST`. Used for research Q&A (M2) and brief progress (M4).
- **Brief generation**: SSE progress events (`step`, `draft`, `done`, `error`); generation runs and is persisted
  server-side independently of the connection, so a closed tab reloads the result with a plain GET.
- **Contract**: OpenAPI from M0. `springdoc-openapi-starter-webmvc-api` 3.1.1 (no Swagger UI; spec exposed in
  `local`/`test` only). `OpenApiSpecIT` writes the spec and fails if the committed `docs/openapi.json` differs;
  `openapi-typescript` 7.13.0 generates `ui/src/api/schema.d.ts`; `ui-verify` regenerates and fails on a diff.
- **Server state**: hand-rolled `useApi` hook; reassess at M4 when mutations must refresh other screens.
- **Compliance in the UI** (rules enforced by the backend, the UI only reflects them):
  - Approve is disabled while any compliance violation is open. The RM edits (the server re-runs
    `compliance.checkText`) or rejects. No "approve anyway" override.
  - The AI marker is never removed: draft states "AI draft, not reviewed" + link to sources; `Approved`
    "AI-generated · reviewed and approved by {rm}, {date}"; `Rejected` "AI draft, rejected by {rm}";
    `isFallback` "Built from a template (AI unavailable)". It applies to every AI text: news rationale, daily
    summary, research answers, briefs.
  - LLM output is untrusted and always rendered as text. ESLint bans `dangerouslySetInnerHTML`.
- **Stack** (versions verified on npm 2026-10-18; each added in the milestone that first needs it):
  M0 react/react-dom 19.3.1, vite 8.3.4, @vitejs/plugin-react 6.1.2, typescript 5.9.3, @types/react(-dom) 19.3.0,
  tailwindcss + @tailwindcss/vite 4.3.3, @fontsource/inter + ibm-plex-mono 5.3.0 (self-hosted fonts), eslint 10.13.0,
  typescript-eslint 8.71.2, eslint-plugin-react-hooks 7.1.1, vitest 5.0.5, jsdom 30.1.3, @testing-library/react
  16.3.3, @testing-library/dom 10.4.2, @testing-library/jest-dom 7.0.1, openapi-typescript 7.13.0;
  M1 react-router 8.4.0; M3 recharts 3.10.1 (+ react-is 19.3.1); M4 zustand 5.0.15, @testing-library/user-event
  14.6.8, @playwright/test 1.64.1. TypeScript is pinned to 5.9.3 because openapi-typescript requires `^5.x` and
  typescript-eslint `<6.1`. Node 24 in `ui/.nvmrc`.
- **Testing**: `tsc --noEmit`; ESLint; Vitest + Testing Library (jsdom) for pure functions (formatters, SSE parser,
  chart mappers) and compliance-relevant components (AI marker, sentiment label, status → allowed actions); `fetch`
  stubbed with `vi.spyOn`, no MSW; Playwright (Chromium only) smoke "login → brief → marker visible → approve"
  against `demo` (local in M4, CI in M6). No snapshot, visual-regression or chart-rendering tests, no Storybook,
  no coverage threshold.
- **CI**: `ui-verify` job in `ci.yml` (`npm ci`, lint, typecheck, API types up to date, unit tests, build);
  Dependabot `npm` for `/ui` with grouped updates.

## Consequences

- Each milestone ends with a demoable screen; the API gets a real consumer from M0.
- Cookie auth adds CSRF handling and a cookie-reading JWT resolver in `access` (M4). Same-origin is now a hard
  requirement in every profile.
- OpenAPI adds one Maven and one npm dependency and a committed `docs/openapi.json`; controller changes must
  regenerate it (CI enforces).
- The fixed TypeScript 5.9.3 pin will need a revisit when openapi-typescript supports TS 6/7.
- Node 24 enters maintenance on 2026-10-20; Node 26 becomes LTS on 2026-10-28. Switch `.nvmrc` after that.
- Review carefully (owner is learning frontend): API client and CSRF handling, SSE parser, auth/route guard, anything
  rendering LLM text, AI marker and status → actions logic, number formatting, Spring Security/SPA fallback/
  Dockerfile, and test assertions. Generated code is acceptable for layout/styling, chart wiring and config.
- Revisit: server-state library at M4; `demo` serving if a live demo host is chosen (DESIGN 18); the approval
  override (Domain Questions Log).
