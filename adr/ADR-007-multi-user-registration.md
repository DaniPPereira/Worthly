# ADR-007 --- Multi-user registration and app-connected providers

**Status:** Accepted  
**Supersedes:** ADR-003 (single-owner-only registration)

## Decision

Worthly is multi-tenant at the user boundary. `POST /api/v1/register`
creates an isolated user. Each user's accounts, connections,
transactions and investments are scoped by `user_id`.

Registration does not issue OAuth tokens. The user then signs in through
the existing Authorization Code + PKCE flow (web BFF or mobile).

Optional `WORTHLY_BOOTSTRAP_*` secrets may still seed the first user on
an empty database. They do not block further registration. An empty
database with no bootstrap secrets is healthy because registration is
available.

## Trading 212

Users add a read-only Trading 212 API key and secret from web or mobile
Connections (`POST /api/v1/connections/trading-212`). Worthly encrypts
the pair at rest (`provider_connection.credentials_encrypted`, AES-GCM)
and never returns it in API responses, logs or audit metadata.

Enable Banking remains consent-backed from the same Connections screens.
Server env keys are an optional local/IT fallback, not the product path
for attaching accounts.

## Recovery

No email reset. Host administrators may still run
`worthly owner reset-password` for a known user. Lockout remains
5 failed logins in 15m → 15m lock.
