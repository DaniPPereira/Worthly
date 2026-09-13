# Implementation status

Current phase: **Phase 5 — Worthly Web**
Current branch: `phase/5-worthly-web`

Phase 0: `phase/0-foundation` (pushed)
Phase 1: `phase/1-enable-banking` (pushed)
Phase 2: `phase/2-banking-domain` (pushed)
Phase 3: `phase/3-categorization-analytics` (pushed)
Phase 4: `phase/4-trading-212` (pushed)

## Completed in this branch

- Next.js BFF UI matching the Worthly Web identity (pine / paper / brass, Rising W, desktop sidebar)
- Screens: Dashboard, Transactions, Investments, Accounts, Connections, Settings
- Privacy mode hides amounts in this browser only (not tokens)
- Totals stay grouped by currency; the UI never sums EUR+USD
- Sync, Enable Banking authorize/reauth, disconnect/purge, CSV export, category/notes, transfer confirm/reject
- Branded `/login` landing; OAuth start remains `/login/start`

## Tests executed

| Area | Result | Evidence |
| --- | --- | --- |
| Money/period helpers | **TESTED LOCALLY** | `money.test.ts`, `period.test.ts` |
| Existing BFF session/PKCE | **TESTED LOCALLY** | `session.test.ts`, `pkce.test.ts` |
| Live owner session against API | **NOT YET VERIFIED** | needs local Compose + owner login |

## Phase 5 acceptance

- [x] Dashboard consumes currency-grouped aggregate DTOs
- [x] Transactions filters, category/notes, transfer confirm/reject
- [x] Investments summary and positions
- [x] Accounts balances with timestamps
- [x] Connections authorize, reauth, T212 configuration status, sync runs
- [x] Settings timezone/currency, privacy, logout, devices
- [ ] Browser E2E against a running API (deferred)

## Deferred

- Fake 6-month net-worth / portfolio charts (v1 has no historical wealth snapshots)
- Position return % (no cost basis in the API)
- 2FA / weekly-email toggles from the design prototype (no API)
- Playwright E2E
- Real-account Enable Banking and Trading 212 proof
