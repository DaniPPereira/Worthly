# Roadmap

## Phase 0 --- Foundation (implementation-ready)

Scope ONLY: - monorepo; - Java/Spring API skeleton; -
PostgreSQL/Flyway; - schema V1 identity/core tables; - Spring
Authorization Server/Security; - owner bootstrap; - Web BFF
login/session; - Mobile OAuth client registration + PKCE contract; -
OpenAPI validation; - Docker Compose; - CI/security scans; - health
endpoints; - ADRs.

No dashboard. No real bank sync.

Exit: all `implementation/PHASE_0.md` acceptance tests pass.

## Phase 1 --- Enable Banking proof

-   dynamic ASPSP discovery;
-   authorization start/callback/session completion;
-   one owned account in restricted production;
-   account reconciliation via identification_hash;
-   balances;
-   transactions;
-   idempotent importer;
-   rate-limit and reauth handling.

Exit: one real account syncs twice with zero duplicates.

## Phase 2 --- Banking domain

Santander + Revolut, connection health, sync scheduling, normalized
accounts/balances/transactions.

## Phase 3 --- Categorization/transfers/analytics

Taxonomy, rules, matching, formulas, exports.

## Phase 4 --- Trading 212

Configuration-backed connection reconciliation, read-only credential
validation, summary, positions and history; reconcile investment funding.
Phase exit requires deterministic `secret present -> connection upsert`
behavior and `CONFIGURATION_REQUIRED` behavior when credentials are
missing/invalid.

## Phase 5 --- Worthly Web

Dashboard, accounts, transactions, investments, settings/connections.
Dashboard/analytics MUST consume currency-grouped aggregate DTOs; no
component may assume one global currency total before an FX subsystem
exists.

## Phase 6 --- Worthly Mobile

Flutter client, PKCE auth, biometrics, secure storage, screens, deep
links and push.

## Phase 7 --- Hardening/MVP release

ASVS/MASVS review, backup restore, 100k perf test, provider outage
tests, audit/log redaction, mobile release checks.

## Phase 8 --- Optional

Additional banks/assets, budgets/goals, FX, recurring-payment detection,
richer insights. Payment initiation/trading require a separate
security/product ADR and are not a normal v1 extension.
