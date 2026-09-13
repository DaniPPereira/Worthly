# Implementation status

Current phase: **Phase 4 — Trading 212**
Current branch: `phase/4-trading-212`

Phase 0: `phase/0-foundation` (pushed)
Phase 1: `phase/1-enable-banking` (pushed)
Phase 2: `phase/2-banking-domain` (pushed)
Phase 3: `phase/3-categorization-analytics` (pushed)

## Completed in this branch

- Configuration-backed unique `TRADING_212` connection (secrets never stored on the row)
- Missing credentials -> `CONFIGURATION_REQUIRED`; HTTP 401/403 -> `ERROR`; transient failures keep `ACTIVE`
- Read-only sync of account summary, positions, cash movements and dividends
- `GET /investments/summary` and `/investments/positions`
- Wealth summary includes brokerage cash + portfolio value per currency
- Bank -> Trading 212 deposits auto-classified as `INVESTMENT_FUNDING`

## Tests executed

| Area | Result | Evidence |
| --- | --- | --- |
| Secret present/absent/401 lifecycle | **TESTED LOCALLY** | `Trading212ConnectionServiceTest`, `RateLimitHeadersTest` |
| Sync, investments API, funding match, analytics | **TESTED WITH MOCK** | `Trading212IT` |
| Phase 0–3 suite | **TESTED LOCALLY** | `./gradlew test` |
| Live Trading 212 account | **NOT YET VERIFIED** | needs owner read-only key |

## Phase 4 acceptance

- [x] Secret present upserts exactly one T212 connection
- [x] Missing/invalid config is `CONFIGURATION_REQUIRED` / `ERROR`
- [x] Summary, positions and history import
- [x] Investment funding is not consumption expense
- [ ] Live Trading 212 proof (deferred)

## Deferred

- Web/mobile UI (Phases 5–6)
- Real-account Enable Banking and Trading 212 proof
