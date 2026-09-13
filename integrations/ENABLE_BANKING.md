# Enable Banking Contract

## Base

`https://api.enablebanking.com`

Backend authenticates requests using JWT signed with application RSA
private key.

## ASPSP discovery

`GET /aspsps?country=PT`

Do not store a fictional ASPSP ID. Enable Banking documents ASPSP
uniqueness by `name + country`. For Santander use the returned exact
`Banco Santander Totta` entry in Portugal. Re-fetch before
reauthorization because names can change.

## Authorization

1.  `POST /auth`
2.  Request AIS access: balances + transactions; `psu_type=personal`;
    exact ASPSP name/country; Worthly HTTPS callback; random state.
3.  Redirect user to returned URL.
4.  Callback receives `code` + `state` or OAuth-style error.
5.  Validate state.
6.  `POST /sessions` with authorization code.
7.  Persist session metadata and accounts.

## Accounts

Use session response/account resources. Persist: - external session
ID; - external account ID alias; - `identification_hash`; - masked
identifier; - currency/type/name where available.

Reauthorization changes session/account IDs; reconcile with
`identification_hash`.

## Balances

`GET /accounts/{account_id}/balances`.

Persist every returned balance type and observation/reference
timestamps.

## Transactions

`GET /accounts/{account_id}/transactions` with `date_from`, `date_to`,
`continuation_key`, `transaction_status` as needed. Follow continuation
until exhausted. Never assume page size/order.

## PSU headers

For owner-initiated online refresh, forward the permitted PSU context
headers through a trusted backend path where appropriate. Never trust
arbitrary spoofed client IP without reverse-proxy normalization.

## Rate limits

ASPSP-specific. On `ASPSP_RATE_LIMIT_EXCEEDED`/429 during background
fetch, default next attempt \>=6h unless provider response gives a later
reset. Manual online refresh may use online PSU context but still
respects provider limits.

## Session states

Map provider session status to local: AUTHORIZED -\> ACTIVE
EXPIRED/REVOKED/INVALID -\> REAUTH_REQUIRED or ERROR according to
semantics CLOSED -\> DISABLED

## Retention

Raw provider payload encrypted and retained 30 days by default for
troubleshooting/reconciliation; normalized data retained until owner
deletes it.
