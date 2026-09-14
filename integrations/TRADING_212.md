# Trading 212 Contract

## Environments

Demo: `https://demo.trading212.com/api/v0`\
Live: `https://live.trading212.com/api/v0`

## Authentication

Basic authentication using Base64 of API key + secret as documented by
Trading 212. Dedicated read-only key. IP restrict to Worthly egress IP
when feasible.

Users paste the key and secret in web or mobile Connections. Worthly
encrypts the pair at rest (`provider_connection.credentials_encrypted`)
and never returns it to clients, logs or audit metadata.

## V1 endpoints

-   `GET /equity/account/summary`
-   `GET /equity/positions`
-   `GET /equity/history/dividends`
-   `GET /equity/history/transactions`
-   `GET /equity/history/orders` only if needed for portfolio
    history/reconciliation

Worthly exposes NO order placement endpoint.

## Account support

Public API currently applies to Invest and Stocks ISA only. Trading 212
Crypto is a separate account (Trading 212 Markets Ltd) and is **not**
exposed by this API. Worthly cannot import crypto wallets, holdings or
cash from that product.

Worthly must fail configuration clearly if account type/API access is
unsupported.

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

Trading 212 is connected from the app, not by writing secrets into the
server environment.

1. Authenticated `POST /connections/trading-212` with `{ apiKey, apiSecret }`.
2. Upsert exactly one `(user, TRADING_212)` `provider_connection`.
3. Encrypt credentials at rest. Never include them in `Connection`
   responses.
4. Validate with a lightweight `accountSummary` call. Valid credentials
   -> `ACTIVE`. HTTP 401/403 -> `ERROR`. Other failures -> `ERROR` with
   `last_error_code`.
5. If stored credentials are absent (and no local/IT env fallback),
   preserve history and set `CONFIGURATION_REQUIRED`. Never delete the
   connection row because secrets disappeared.
6. Startup reconciliation does not auto-create a Trading 212 connection
   from server env for every user.

The database MUST enforce uniqueness for the user's Trading 212
connection (`uq_provider_connection_t212` in `database/SCHEMA.md`).

This lifecycle is different from Enable Banking consent-backed
connections and is implemented behind the same provider connection
domain abstraction.
