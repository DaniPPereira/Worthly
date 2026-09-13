# Security Specification

## Baseline

Target OWASP ASVS Level 2; apply OWASP API Security Top 10 and OWASP
MASVS principles for mobile. Read-only financial access is mandatory in
v1.

## Authentication

Locked by ADR-002: - local single-owner identity; - Spring Authorization
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

Owner is bootstrapped from server-side secret file. Password hashing:
Argon2id using Spring Security's maintained encoder with parameters
calibrated so verification is approximately 250-500ms on production
hardware. Never hardcode Argon2 parameters without benchmark. Minimum
password length 14; allow passphrases; no arbitrary composition rules.

No public registration. No email-based password reset in v1. Recovery is
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

-   5 failed attempts / 15 minutes -\> temporary 15 minute lock for
    owner login;
-   exponential delay may supplement lockout;
-   audit success/failure without password details;
-   reverse proxy rate limit auth endpoints.

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
