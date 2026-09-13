# Worthly Mobile

Flutter client for Android and iOS. It is never a financial-provider
client.

Minimum OS: **Android API 26** (8.0) and **iOS 16**. One codebase;
release signing keys never in Git.

## Auth

OAuth Authorization Code + PKCE. Mobile is a public client
(`token_endpoint_auth_method=none`). Access token 10m. Rotating refresh
token stored in Keychain/Keystore-backed secure storage. Biometrics
unlock local use; server authorization remains authoritative.

## Screens

Home, Transactions, Investments, Accounts, Connections, Settings,
Devices/Sessions.

## Deep links

Verified HTTPS Universal Links/App Links. Authorization callback must be
state-bound and single-use. Custom schemes are fallback only.

## Privacy

Hide balances toggle, protected app-switcher snapshot where practical,
generic locked-screen push content, no raw bank payload local cache.

## Offline

Optional minimal encrypted/protected snapshot. Show `lastUpdatedAt`.
Mutations requiring server validation fail clearly while offline; do not
queue ambiguous financial edits.

## Push

Connection reauth, repeated sync failure, optional milestones/insights.
Push carries opaque notification ID; app fetches detail after auth.

## Locale

UI copy is English in v1. Number and date formatting use `pt-PT` by
default (owner in Portugal). Category labels may later be localized
without changing system codes.

## Accessibility

Dynamic type, screen readers, semantic controls, sufficient targets,
textual chart summaries and no color-only meaning.
