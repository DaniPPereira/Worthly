# Implementation status

Current phase: **Phase 1 — Enable Banking proof**
Current branch: `phase/1-enable-banking`

Phase 0 remains on `phase/0-foundation` (pushed).

## Completed in this branch

- Flyway `V3__banking.sql`: connections, authorization attempts, accounts, balances, transactions, sync runs
- Enable Banking JWT signer (RS256, `kid` = application id)
- Discovery adapter `GET /aspsps` with 15-minute cache and v1 filter (`Banco Santander Totta` in PT + names starting with `Revolut`)
- `POST /connections/enable-banking/authorize` stores hashed state (10m TTL)
- Unauthenticated callback exchanges `code`, rejects replay, persists encrypted session id and accounts
- `identification_hash` reconciliation keeps a stable local account id
- Sync: balances, transactions with `continuation_key`, idempotent second import, 429 → `RATE_LIMITED` with next retry ≥ 6h
- Phase 1 accounts/balances/transactions/list/disconnect/purge APIs
- AES-GCM payload crypto for session ids and raw transaction payloads (30-day retention)

## Tests executed

| Area | Result | Evidence |
| --- | --- | --- |
| Enable Banking JWT header/claims | **TESTED LOCALLY** | `EnableBankingJwtSignerTest` |
| v1 ASPSP filter | **TESTED LOCALLY** | `EnableBankingDiscoveryServiceTest` |
| Discovery, auth, callback replay, dual sync, 429, hash reconcile | **TESTED WITH MOCK** | WireMock `EnableBankingIT` |
| Real Santander/Revolut owned account | **NOT YET VERIFIED** | requires owner Enable Banking app + bank credentials |
| Phase 0 Gradle tests | must stay green on this branch | rerun with Phase 1 |

## Phase 1 acceptance

- [x] Mock: one account imported twice, duplicates = 0
- [x] Mock: callback state replay rejected
- [x] Mock: same local account id when `identification_hash` matches after reauth
- [x] Mock: 429 schedules retry without a tight loop
- [ ] Restricted-production owned Santander or Revolut connection
- [x] Provider fixtures sanitized (fake IBAN / hashes / names)
- [x] Provider private key not in repo

## Deferred

- Real-account exit criterion (needs owner secrets, not in git)
- Playwright / browser E2E
- Trading 212 (Phase 2)
- Dashboard aggregates (later phases)
