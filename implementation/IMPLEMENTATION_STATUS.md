# Implementation status

Current phase: **Phase 2 — Banking domain**
Current branch: `phase/2-banking-domain`

Phase 0: `phase/0-foundation` (pushed)
Phase 1: `phase/1-enable-banking` (pushed)

## Completed in this branch

- Spring `@Scheduled` tick every 15 minutes (disabled in tests)
- Sync policy: ACTIVE + last success older than 3h; wait on `RATE_LIMITED.next_retry_at`; no fetch for REAUTH/CONFIG/DISABLED
- PostgreSQL advisory lock per connection
- 24h reminder notifications: `CONNECTION_REAUTH_REQUIRED`, `SYNC_REPEATED_FAILURE`, `CONFIGURATION_REQUIRED`
- `GET /notifications` and `POST /notifications/{id}/read`
- `GET /transactions` filters (date, account, category, direction, lifecycle, economic type, text, amount range)
- CSV export with the OpenAPI column set
- Dual-bank (Santander + Revolut) mock coverage
- Pending → booked does not duplicate (FR-032)

## Tests executed

| Area | Result | Evidence |
| --- | --- | --- |
| Sync policy decisions | **TESTED LOCALLY** | `SyncPolicyTest` |
| Dual bank, filters, CSV, scheduler tick, pending→booked, reauth notify | **TESTED WITH MOCK** | `BankingDomainIT` |
| Phase 0/1 suite | **TESTED LOCALLY** | `./gradlew test` green |
| Real Santander + Revolut together | **NOT YET VERIFIED** | needs owner Enable Banking app |

## Phase 2 acceptance

- [x] Scheduler rules encoded and unit-tested
- [x] Mock Santander + Revolut connections
- [x] Connection health via status + notifications
- [x] Transaction filters + CSV
- [ ] Real dual-bank restricted production

## Deferred

- Categorization, transfer matching, analytics (Phase 3)
- Trading 212 (Phase 4)
- Web/mobile UI (Phases 5–6)
- Real-account Enable Banking proof
