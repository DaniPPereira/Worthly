# Implementation status

Current phase: **Phase 0 — Foundation**
Current branch: `phase/0-foundation`

## Completed in this branch

- Monorepo layout: `worthly-api`, `worthly-web`, `worthly-mobile`, `infrastructure`
- Spring Boot 3.5.16 API, Java 21, Flyway identity + SAS JDBC schema
- Single-owner bootstrap (ADR-003): no `/register`, restart does not create a second owner
- Spring Authorization Server + Resource Server (ADR-002)
- Clients: `worthly-web` confidential + PKCE, `worthly-mobile` public PKCE
- Scopes: `openid profile worthly.read worthly.write`
- `/api/v1/me`, `PATCH /me`, `POST /me/logout` (204), `/devices`, device revoke
- Refresh rotation + reuse detection + logout family revoke
- Next.js BFF: encrypted HttpOnly `web_session` cookie, CSRF on mutations, Bearer proxy
- Flutter PKCE skeleton + secure-storage wrapper + `/me` client
- Docker Compose (Postgres published only in the local file)
- Production Compose keeps Postgres on the Compose network only
- CI: secret scan, Gradle Testcontainers, web lint/test, Flutter analyze/test

## Tests executed

| Area | Result | Evidence |
| --- | --- | --- |
| `worthly-api` Gradle Testcontainers | **TESTED LOCALLY** | `./gradlew test` green on 2026-09-13 (Docker Desktop up) |
| OpenAPI 4.1.0 parse | **TESTED LOCALLY** | `OpenApiContractTest` |
| Auth code + PKCE + `/me` + logout + refresh reuse + device revoke | **TESTED LOCALLY** | `AuthFlowIT` |
| Web BFF cookie flags / PKCE helper | **TESTED LOCALLY** | `npm test` (3 passing) + `npm run lint` |
| Web browser login E2E | **NOT YET VERIFIED** | needs running API + BFF in a browser |
| Flutter unit tests | **NOT YET VERIFIED locally** (SDK not installed); expected in CI |
| Production Compose bring-up | **NOT YET VERIFIED** | requires a real PKCS#8 signing key |
| Secret scan | **IMPLEMENTED**; CI job present | local `git grep` for PEM pending commit |

## Phase 0 acceptance

- [x] Fresh DB + bootstrap secrets → one owner (`OwnerBootstrapIT`)
- [x] Restart → no second owner
- [x] No `/register`
- [x] Web BFF stores tokens only in HttpOnly `web_session` (unit-level cookie flags)
- [ ] Web login exercised in a real browser
- [x] Mobile has no client secret; PKCE S256 unit tests
- [x] Refresh rotation and reuse detection (`AuthFlowIT`)
- [x] Device revoke prevents refresh (`AuthFlowIT`)
- [x] `/me` 401 without auth
- [x] `PATCH /me` rejects invalid timezone
- [x] `POST /me/logout` revokes refresh family
- [x] Postgres not published in `infrastructure/compose.prod.yaml`
- [x] OpenAPI parses
- [x] Secret-scan workflow present

## Deferred

- Dashboard and financial sync (Phase 1+)
- Playwright E2E against Compose
- Real PKCS#8 signing-key provisioning outside tests
- iOS/Android platform projects from `flutter create` (unit tests do not need them)

## ADRs followed

ADR-001 modular monolith, ADR-002 auth boundary, ADR-003 bootstrap, ADR-006 mobile secure storage.
