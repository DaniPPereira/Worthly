# Implementation status

Current phase: **Phase 3 — Categorization, transfers, analytics**
Current branch: `phase/3-categorization-analytics`

Phase 0: `phase/0-foundation` (pushed)
Phase 1: `phase/1-enable-banking` (pushed)
Phase 2: `phase/2-banking-domain` (pushed)

## Completed in this branch

- Flyway `V5` system taxonomy, categorization rules, transfer matches
- Static merchant heuristic fixture (EQUALS then CONTAINS keys longer than 4)
- User rules by priority; manual overrides never overwritten on sync
- PATCH `/transactions/{id}` distinguishes omitted vs null `categoryId`/`notes`
- Transfer matching with confidence thresholds, reject fingerprint, CSV match id
- Analytics summary (liquid cash + investment 0 until Trading 212) and monthly formulas
- CSV `categoryCode` / `categoryLabel` / `transferMatchId`

## Tests executed

| Area | Result | Evidence |
| --- | --- | --- |
| Normalizer, heuristic vs rule vs manual, transfer score, monthly formula | **TESTED LOCALLY** | `TextNormalizerTest`, `CategorizationServiceTest`, `TransferScoreTest`, `MonthlyTotalsTest` |
| Seeded categories, rules, auto/suggest/reject, analytics, CSV | **TESTED WITH MOCK** | `CategorizationAnalyticsIT` |
| Phase 0–2 suite | **TESTED LOCALLY** | `./gradlew test` |
| Real Santander + Revolut together | **NOT YET VERIFIED** | needs owner Enable Banking app |

## Phase 3 acceptance

- [x] Taxonomy seeded and listed
- [x] Rules and heuristics with documented precedence
- [x] Transfer auto-link / suggest / reject
- [x] Per-currency analytics; no cross-currency total
- [ ] Real-account Enable Banking proof (deferred)

## Deferred

- Trading 212 (Phase 4)
- Web/mobile UI (Phases 5–6)
- Real-account Enable Banking proof
