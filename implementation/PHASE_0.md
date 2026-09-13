# Phase 0 --- Exact Implementation Plan

## Goal

Create a secure runnable foundation. No provider sync and no dashboard.

## Repository

``` text
worthly/
  worthly-api/
  worthly-web/
  worthly-mobile/
  infrastructure/
  docs/
```

## API dependencies

Spring Web, Validation, Security, Authorization Server, OAuth2 Resource
Server, Data JPA, Flyway, PostgreSQL driver, Actuator, Testcontainers,
Jackson, Micrometer. Add libraries only with a concrete use.

## Tasks

### P0-01 Repository/CI

Build all three clients, lint, test, secret scan, OpenAPI lint/parse.

### P0-02 PostgreSQL

Compose service, non-public DB port in production profile, Flyway
baseline.

### P0-03 Identity schema

Create user/session/device/refresh/audit tables.

### P0-04 Owner bootstrap

Implement ADR-003 exactly. Integration tests for first bootstrap,
missing bootstrap and second bootstrap refusal.

### P0-05 Authorization server

Register: - `worthly-web` confidential/server-side client as needed by
BFF architecture; - `worthly-mobile` public client, authorization_code,
PKCE S256 required, no client secret; - scopes
`openid profile worthly.read worthly.write`.

Implement signing-key loading from secret; no ephemeral production
signing key.

### P0-06 Web session

Next.js login route/BFF establishes server-side authenticated session
and browser HttpOnly cookie. CSRF protection on mutations.

### P0-07 Mobile auth skeleton

Flutter app can initiate PKCE login against local/dev Worthly auth
server, receive callback, store refresh material through secure-storage
abstraction and call `/api/v1/me`.

### P0-08 API contract

Implement `/me`, `PATCH /me`, `POST /me/logout`, `/devices`,
`/devices/{id}` revoke, health and RFC 9457 errors according to OpenAPI
4.1.0. Bootstrap writes default timezone/currency. Stub future business
endpoints only if contract testing needs them; do not fake financial
data.

### P0-09 Security headers/rate limits

Implement baseline headers, auth endpoint rate limit at proxy/app, log
redaction.

### P0-10 Operations

Docker Compose, `.env.example`, secret-file examples with dummy values,
backup/restore scripts or runbook, Actuator private exposure.

## Exit tests

-   Fresh DB + valid bootstrap secrets -\> exactly one owner.
-   Restart -\> no second owner.
-   No `/register`.
-   Web login works; browser has no OAuth token in localStorage.
-   Mobile PKCE login works in dev; no client secret in app.
-   Refresh token rotation and reuse detection tested.
-   Device revoke prevents refresh.
-   `/me` unauthorized without auth.
-   `PATCH /me` rejects invalid timezone/currency.
-   `POST /me/logout` revokes refresh family.
-   PostgreSQL not publicly exposed in production compose.
-   OpenAPI parses and contract tests pass.
-   Secret scan clean.
