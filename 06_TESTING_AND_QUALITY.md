# Testing and Quality

## Layers

-   Unit: formulas, categories, transfer matching, provider mapping,
    state machines.
-   Integration: PostgreSQL Testcontainers, Flyway, auth/security
    filters, repositories.
-   Provider: WireMock fixtures for pagination, 401/403/429/5xx,
    malformed data, expired sessions.
-   Contract: validate implementation against `api/openapi.yaml`.
-   Web E2E: Playwright.
-   Mobile: Flutter unit/widget/integration tests.

## Mandatory financial invariants

-   import is idempotent;
-   no float money;
-   pending/booked does not double count;
-   manual category wins;
-   internal transfers excluded from income/expense;
-   T212 funding excluded from consumption expense;
-   reporting month uses configured timezone;
-   unsupported currency conversion never happens silently.

## Auth tests

-   bootstrap creates one owner only;
-   no registration endpoint;
-   lockout behavior;
-   Web CSRF;
-   mobile PKCE required;
-   refresh rotation;
-   refresh reuse revokes family;
-   revoked device cannot refresh;
-   callback state replay rejected;
-   owner-bound authorization on every resource.

## Performance

Seed \>=100k transactions. Validate p95 list/filter and aggregate goals.
Use EXPLAIN ANALYZE for slow queries.

## Merge gate

Compile, lint, unit/integration/contract tests, migration validation,
secret scan, dependency scan and image scan. Critical domain paths
require strong meaningful coverage; no vanity coverage target.
