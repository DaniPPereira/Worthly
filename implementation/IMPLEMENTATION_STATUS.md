# Implementation status

Current phase: **Phase 6 — Worthly Mobile**
Current branch: `phase/6-worthly-mobile`

Phase 0: `phase/0-foundation` (pushed)
Phase 1: `phase/1-enable-banking` (pushed)
Phase 2: `phase/2-banking-domain` (pushed)
Phase 3: `phase/3-categorization-analytics` (pushed)
Phase 4: `phase/4-trading-212` (pushed)
Phase 5: `phase/5-worthly-web` (pushed)

## Completed in this branch

- Flutter screens matching the Worthly Mobile identity (pine / paper / brass, Rising W, bottom tabs)
- Screens: Lock, Sign-in, Home, Transactions (bottom sheet), Investments, Accounts, Connections, Settings
- Public PKCE client: system-browser sign-in (`worthly://auth/callback`); no password fields
- Face ID unlocks a local session only; access token stays in memory, refresh token in secure storage
- Privacy mode and app-switcher obscure are local device preferences
- Totals stay grouped by currency; no invented net-worth history or position return %
- Enable Banking authorize/reauth uses `returnClient: MOBILE` and `worthly://connections/result`

## Tests executed

| Area | Result | Evidence |
| --- | --- | --- |
| Money/period helpers | **TESTED LOCALLY** | `money_test.dart`, `period_test.dart` |
| PKCE / token exchange | **TESTED LOCALLY** | `pkce_test.dart`, `auth_repository_test.dart` |
| Lock / sign-in widgets | **TESTED LOCALLY** | `widget_test.dart` |
| Live owner session against API | **NOT YET VERIFIED** | needs device/emulator + local Compose |
| Enable Banking / Trading 212 on device | **NOT YET VERIFIED** | real owned accounts |

## Phase 6 acceptance

- [x] Lock and PKCE sign-in without collecting a password in Flutter
- [x] Home, Transactions, Investments, Accounts, Connections, Settings consume API DTOs
- [x] Category/notes and transfer suggest confirm/reject
- [x] Connections authorize, reauth, T212 configuration copy, sync, disconnect/purge
- [x] Settings timezone/currency, Face ID, privacy, devices, notification inbox, CSV export, logout
- [ ] Device E2E against a running API (deferred)

## Deferred

- Full FCM without `google-services.json`
- Fake 6-month net-worth / portfolio return charts (v1 has no historical wealth snapshots)
- Notification preference toggles from the design prototype (no API)
- Live Enable Banking and Trading 212 on a physical device
