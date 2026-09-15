# Security Specification

## Baseline

Target OWASP ASVS Level 2; apply OWASP API Security Top 10 and OWASP
MASVS principles for mobile. Read-only financial access is mandatory in
v1.

## Authentication

Locked by ADR-002: - local multi-user identity with public registration; - Spring Authorization
Server / Spring Security standards-based OAuth/OIDC; - Web BFF session
cookie; - Mobile Authorization Code + PKCE; - access token TTL 10
minutes; - refresh token absolute TTL 30 days; - refresh token idle TTL
14 days; - refresh token rotation on every use; reuse detection revokes
token family; - Web idle session 30 minutes; absolute session 12
hours; - sensitive operations may require recent authentication (\<10
minutes).

TTL values are configuration but these are production defaults and tests
assume them.

## Password and owner

Users register with email + password (minimum 8 characters). Registration
is public; there is no email verification in v1. Password hashing:
Argon2id using Spring Security's maintained encoder with parameters
calibrated so verification is approximately 250-500ms on production
hardware. Never hardcode Argon2 parameters without benchmark. Allow passphrases; no arbitrary composition rules.

Optional bootstrap from a server-side secret file may create the first
user on an empty database. No email-based password reset in v1. Recovery is
a local administrative CLI/container command requiring host access and a
new password secret file; it revokes all sessions.

## Secrets

Never in Git, frontend bundles, logs or DB dumps where avoidable: -
Enable Banking RSA private key; - Trading 212 API secret; - OAuth
signing keys; - DB password; - push credentials.

Mount as read-only Docker secrets/files or protected secret storage.
File permissions owner-read only where possible.

## Browser

-   Secure, HttpOnly session cookie;
-   SameSite=Lax default;
-   CSRF token for state-changing cookie-auth requests;
-   exact CORS allowlist;
-   CSP;
-   HSTS after HTTPS stable;
-   X-Content-Type-Options nosniff;
-   frame-ancestors none;
-   Referrer-Policy strict-origin-when-cross-origin.

## Mobile

-   iOS Keychain / Android Keystore-backed storage;
-   no tokens in ordinary preferences;
-   verified Universal Links/App Links;
-   biometrics re-unlock local session only;
-   no bank auth in embedded insecure WebView;
-   root/jailbreak is risk signal, not trust boundary;
-   hide/blur sensitive app-switcher snapshot where practical.

## Authorization

Every resource is owner-bound. Even single-user v1 checks ownership in
application layer. Never trust IDs supplied by clients without ownership
lookup.

## Encryption at rest

-   host/full-disk encryption recommended;
-   encrypted backups mandatory;
-   application-level AES-256-GCM envelope encryption for retained raw
    provider payloads and sensitive provider session metadata;
-   per-record random nonce;
-   key version stored with ciphertext;
-   master encryption key outside DB;
-   key rotation supported by versioned decryptor.

Normalized non-secret financial fields remain queryable in PostgreSQL;
do not encrypt every amount if it destroys required analytics. Protect
DB/storage instead.

## Logging/redaction

Never log Authorization headers, cookies, OAuth codes, refresh tokens,
provider JWTs, private keys, T212 secrets, raw IBANs or full raw
payloads. Account IDs are masked or internal opaque IDs.

## Login protection

-   5 consecutive failed passwords -\> 15 minute lock (`locked_until`);
    lock is time-based and does not permanently disable the account;
-   exponential delay may supplement lockout;
-   audit success/failure without password details;
-   application rate limit per client IP, 15 minute window:
    - `POST /login`, `POST /register`, `POST /api/v1/register`: 20;
    - `POST /oauth2/token` from a public IP: 20;
    - `POST /oauth2/token` from loopback/RFC1918 (BFF/Docker): 600,
      so many users refreshing through `worthly-web` are not one bucket;
-   429 is `application/problem+json` with `Retry-After`;
-   reverse proxy rate limit auth endpoints;
-   do not expose the API port publicly; forwarded client IPs are only
    trusted from private proxy hops.

## Provider callbacks

State is 256-bit random, single-use, hashed at rest,
owner/session-bound, TTL 10 minutes. Reject mismatch, replay and expired
state. Redirect destinations are server allowlisted.

## Network

Only reverse proxy 443 public if public access is required. PostgreSQL,
Actuator, metrics and admin UIs are private. Prefer VPN/Tailscale for
admin. No Docker socket mount.

## Security release gate

No release with exposed DB, secrets in repo, write-enabled T212 key,
payment initiation, missing TLS, failing authz tests, untested backup
restore or unresolved known exploitable critical/high vulnerabilities.

## Account deletion

`DELETE /api/v1/me` with `{ "confirm": true }` is the user right-to-erasure
path for hosted web. The API disconnects and purges every connection,
deletes sessions and tokens, records `ACCOUNT_DELETED`, nulls
`audit_event.user_id`, then deletes `app_user`. Settings requires the
user to type `DELETE` before the request is sent.

## Encrypted raw payloads

`external_transaction.raw_payload_encrypted` is envelope-encrypted and
given `raw_expires_at` = import time + 30 days. `RawPayloadRetentionJob`
runs hourly (`0 20 * * * *`) and nulls expired blobs. Setting the expiry
without a job is not sufficient.

## Hosted web closeout

This instance is operator-hosted (users on the public website, not a
personal-only self-host). Mobile hardening is out of scope for this
pass. The web bar is:

| Requirement | Implementation |
| --- | --- |
| Account deletion | `DELETE /api/v1/me` + Settings confirm UI |
| 30-day raw payload wipe | `RawPayloadRetentionJob` |
| Web idle 30 min / absolute 12 h | BFF `web_session` cookie `maxAge` 1800s; `issuedAt` checked on every proxy; sliding cookie on activity |
| HSTS | Spring Security on API/login; Next middleware in production; `forward-headers-strategy: framework` in prod |
| Rate-limit auth | Per public IP 20/15m on login+register; BFF/Docker token 600/15m |
| Privacy + Terms | `/privacy`, `/terms` linked from login, register, settings |
| Open registration | `POST /api/v1/register`; no invite in the product UI |
| Encrypted backups | `infrastructure/backup/backup.sh` + `WORTHLY_BACKUP_KEY_FILE` |

Production must set `WORTHLY_BACKUP_KEY_FILE`. OpenAPI 4.3.0 is the HTTP
contract for this closeout.

