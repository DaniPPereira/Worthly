# ADR-002 --- Web + Mobile Authentication

**Status:** Accepted

## Decision

Worthly uses Spring Security + Spring Authorization Server as the local
identity/OAuth authority.

### Web

Next.js acts as a confidential OAuth client and BFF. Browser receives
only a Worthly `web_session` cookie (`Secure`, `HttpOnly`,
`SameSite=Lax`). OAuth tokens/credentials are server-side and never
placed in browser storage.

**BFF -> API contract is unambiguous:** Next.js obtains/maintains the
owner OAuth access token server-side and calls Worthly API with
`Authorization: Bearer <access_token>`. Worthly API is a Spring OAuth2
Resource Server and **does not read or authenticate the `web_session`
cookie**. The cookie is meaningful only to the BFF. This keeps one API
authentication contract for both Web and Mobile.

### Mobile

Flutter is a public OAuth client using Authorization Code + PKCE (S256).
No client secret is embedded. Access tokens are 10 minutes. Refresh
tokens rotate on every use, have 14-day idle and 30-day absolute expiry,
and are stored in OS secure storage.

### API

Protected resources require a valid Bearer access token and authenticated
owner identity. The API never authenticates the browser BFF cookie.
Token/session revocation is enforced. Scope set for v1: `openid`, `profile`,
`worthly.read`, `worthly.write`. Provider write capabilities remain
absent.

`POST /api/v1/me/logout` revokes the current token's refresh family and
the associated `web_session` / `oauth_device_session`. The BFF MUST
clear the cookie after a successful logout.

### Redirect URIs (allowlist)

Registered at the Authorization Server; not wildcards:

- Web production: `https://<worthly-web-host>/auth/callback`
- Web local: `http://localhost:3000/auth/callback`
- Mobile: `https://<worthly-links-host>/auth/callback` (Universal Link /
  App Link)
- Enable Banking callback (API, not OAuth client):
  `https://<worthly-api-host>/api/v1/connections/enable-banking/callback`
- Enable Banking local:
  `http://localhost:8080/api/v1/connections/enable-banking/callback`

Hosts are configuration. Redirects outside this allowlist are rejected.

### Where Web tokens live

Next.js is a **separate process** from Spring Boot. It does not query
PostgreSQL and does not share in-process identity services with the API.

After the authorization-code exchange, the BFF stores access token,
refresh token and expiry in an **encrypted HttpOnly cookie** (BFF
server secret; not readable by JavaScript). That cookie is the
`web_session` cookie.

On each browser request the BFF decrypts the cookie, refreshes via
`/oauth2/token` when the access token is expired, and calls Worthly API
with `Authorization: Bearer`. The API never sees the cookie.

The `web_session` table stores only revocation metadata (hashed session
id, expiry, user-agent hash). It is written by the identity module when
login succeeds (BFF calls SAS; SAS/identity persists the row) and when
`POST /me/logout` or device revoke runs. It is not the token store.

## Why

One standards-based identity authority supports both clients without
inventing a custom protocol. PKCE is appropriate for native/public
clients; the BFF keeps browser tokens out of JavaScript.

## Not chosen

-   Third-party cloud IdP: unnecessary external dependency for
    self-hosted v1.
-   JWT in browser localStorage: rejected due to token theft exposure.
-   Separate auth stacks for Web/Mobile: rejected due to duplicated
    policy.
