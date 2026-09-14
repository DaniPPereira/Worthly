# Integration Overview

## Enable Banking

Use AIS only. Discover ASPSPs dynamically with `GET /aspsps`; do not
hardcode imaginary IDs. Enable Banking identifies an ASPSP by
`name + country`; Worthly stores the current pair and refreshes
discovery before reauthorization.

Production targets: - Banco Santander Totta, country `PT`, personal PSU,
AIS. - Revolut: discover dynamically from `GET /aspsps` for the
applicable country/market and select the returned exact name; do not
assume a permanent static ASPSP ID.

Flow: `GET /aspsps` -\> `POST /auth` -\> browser bank SCA -\> callback
code/state -\> `POST /sessions` -\> accounts -\> balances/transactions.

Use `identification_hash` to reconcile an account across reauthorization
because Enable Banking session/account IDs can change.

## Trading 212

Use live base `https://live.trading212.com/api/v0` only in production;
demo base for development. V1 consumes account summary, positions,
historical dividends/orders/transactions as required. API key is
read-only and IP restricted where practical.

Rate limits are endpoint-specific. Adapter reads returned
`x-ratelimit-*` headers and schedules accordingly; never encode a
guessed global number.

## Push

FCM/APNs is an intentional managed transport dependency. Worthly remains
self-hosted for financial data and business logic. Push contains no
sensitive details and is optional; app remains functional without it.


## Trading 212 connection source of truth

Trading 212 is connected from the apps with a read-only API key + secret.
Worthly encrypts the pair on the connection row. Missing credentials
preserve historical data and mark the connection `CONFIGURATION_REQUIRED`.

## Provider connection status

Canonical v4 statuses:
`ACTIVE`, `REAUTH_REQUIRED`, `CONFIGURATION_REQUIRED`, `ERROR`,
`DISABLED`.

`REAUTH_REQUIRED` is primarily consent/SCA lifecycle state.
`CONFIGURATION_REQUIRED` means provider credentials or configuration are
missing or invalid and must be completed from the app (or, for local/IT
fallback, operator configuration).
