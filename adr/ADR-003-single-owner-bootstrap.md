# ADR-003 --- Single Owner Bootstrap and Recovery

**Status:** Accepted

## Bootstrap

There is no `/register`.

On startup, if `users` is empty: 1. API requires
`WORTHLY_BOOTSTRAP_EMAIL` and `WORTHLY_BOOTSTRAP_PASSWORD_FILE`. 2.
Password file is read once. 3. Owner is created and password hashed. 4.
Application refuses a second bootstrap while a user exists. 5.
Production runbook instructs removal of bootstrap secret after success.

If DB is empty and bootstrap inputs are absent, readiness is
`OUT_OF_SERVICE` with a safe bootstrap-required reason.

## Recovery

No email reset in v1. Host administrator runs a dedicated CLI/container
command:
`worthly owner reset-password --password-file /run/secrets/new_password`

The command requires direct deployment/host access, changes hash and
revokes all Web sessions, refresh-token families and device sessions.

## Lockout

5 failed logins in 15m -\> 15m lock. Successful login resets counter.
