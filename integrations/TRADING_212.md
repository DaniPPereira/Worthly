# Trading 212 Contract

## Environments

Demo: `https://demo.trading212.com/api/v0`\
Live: `https://live.trading212.com/api/v0`

## Authentication

Basic authentication using Base64 of API key + secret as documented by
Trading 212. Secret only in backend. Dedicated read-only key. IP
restrict to Worthly egress IP when feasible.

## V1 endpoints

-   `GET /equity/account/summary`
-   `GET /equity/positions`
-   `GET /equity/history/dividends`
-   `GET /equity/history/transactions`
-   `GET /equity/history/orders` only if needed for portfolio
    history/reconciliation

Worthly exposes NO order placement endpoint.

## Account support

Public API currently applies to supported Invest/Stocks ISA account
types per provider documentation. Worthly must fail configuration
clearly if account type/API access is unsupported.

## Rate limits

Read `x-ratelimit-limit`, `x-ratelimit-period`, `x-ratelimit-remaining`,
`x-ratelimit-reset`, `x-ratelimit-used` on responses. Limits are per
account and endpoint-specific. Do not hardcode a global guessed quota.

## Sync

Account summary/positions are snapshots. History uses provider
pagination/parameters according to current OpenAPI. Persist provider
event identity and idempotency keys. Provider schema changes are
isolated in adapter DTOs.


## Connection lifecycle

Trading 212 uses a **configuration-backed connection**.

At application startup and whenever provider configuration is refreshed:

1. Resolve the owner.
2. Check whether both Trading 212 API key and secret are present in
   server-side secret/configuration storage.
3. Upsert exactly one `(owner, TRADING_212)` `provider_connection`.
4. If credentials are present, perform a lightweight authenticated
   validation at first sync/configuration check. Valid credentials ->
   `ACTIVE`. HTTP 401/403 from Trading 212 -> `ERROR` with
   `last_error_code`. Other transient failures do not change a previous
   `ACTIVE` until the sync run classifies them.
5. If credentials are absent, preserve history and set
   `CONFIGURATION_REQUIRED`. Never delete the connection row because
   secrets disappeared.
6. Never persist API key/secret in `provider_connection`, logs, audit
   metadata, browser or mobile storage.

The database MUST enforce uniqueness for the owner's Trading 212
connection (`uq_provider_connection_t212` in `database/SCHEMA.md`).

This lifecycle is different from Enable Banking consent-backed
connections and is implemented behind the same provider connection
domain abstraction.
