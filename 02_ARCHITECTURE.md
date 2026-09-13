# Architecture

## System context

``` text
Worthly Web (Next.js) --------                               >--- Worthly API (Spring Boot) --- PostgreSQL
Worthly Mobile (Flutter) -----/            |
                                           +--- Enable Banking --- Santander/Revolut
                                           +--- Trading 212
                                           +--- FCM/APNs adapter
```

Web and Mobile never call financial providers directly.

## Style

Modular monolith with hexagonal boundaries. Do not introduce Kafka,
Redis, Kubernetes or microservices in v1 without an ADR proving the
need.

## Backend modules

``` text
com.worthly
  identity
  devices
  connections
  banking.accounts
  banking.transactions
  categorization
  transfers
  investments
  analytics
  notifications
  exports
  sync
  audit
  shared
  infrastructure
```

Each business module may contain `domain`, `application`, `adapter.in`,
`adapter.out`.

## Trust boundaries

1.  Browser/mobile are untrusted clients.
2.  Worthly API is the application trust boundary.
3.  PostgreSQL contains sensitive financial data.
4.  Provider APIs are untrusted external systems.
5.  Push transport is untrusted for confidentiality.

## Sync coordinator

-   one active sync per provider connection;
-   DB advisory/application lock per connection;
-   bounded retries for transient failures;
-   401/403 -\> auth/permission classification;
-   429 -\> honor provider reset/Retry-After and schedule later;
-   Enable Banking background-rate-limit default fallback: retry no
    sooner than 6 hours after ASPSP rate-limit failure;
-   persist checkpoint only after successful commit;
-   every run has correlation ID and counts.

### Scheduler

Spring `@Scheduled` inside the monolith (no extra worker process).

-   Tick every 15 minutes.
-   For each `ACTIVE` connection: start a scheduled sync if
    `last_successful_sync_at` is null or older than **3 hours**, and no
    run is queued/running.
-   `RATE_LIMITED`: do not start before `next_retry_at` (minimum 6 hours
    after ASPSP 429 if the provider gave no reset).
-   `REAUTH_REQUIRED`, `CONFIGURATION_REQUIRED`, `DISABLED`: no
    provider fetch. At most one reminder notification per 24 hours.
-   Stale alert: `ACTIVE` and no success for **24 hours** ->
    `SYNC_REPEATED_FAILURE`.

## Provider ports

Banking and investments remain separate.

``` java
interface BankingDataProvider {
  AuthorizationStart startAuthorization(...);
  ConnectionSnapshot completeAuthorization(...);
  List<ExternalAccount> accounts(...);
  List<ExternalBalance> balances(...);
  TransactionPage transactions(...);
}

interface InvestmentDataProvider {
  InvestmentAccountSnapshot accountSummary();
  List<PositionSnapshot> positions();
  InvestmentEventPage history(...);
}
```

## API

Canonical contract: `api/openapi.yaml`. Implementation MUST conform to
it. Contract changes require updating OpenAPI first or in the same PR.

## Persistence

PostgreSQL is source of truth for Worthly state. Provider data is mapped
into local immutable-ish source records plus normalized records. See
`database/SCHEMA.md`.

## Web

Next.js is a BFF/client for Worthly. Browser auth is session-cookie
based; OAuth tokens are not exposed to browser JavaScript.

### Web screens (Phase 5)

Bottom-equivalent primary nav, desktop sidebar allowed:

-   Home — wealth summary, month totals, recent movements, connection health
-   Transactions — filters matching `GET /transactions`, category/notes, transfer confirm/reject
-   Investments — summary, positions, events
-   Accounts — balances, types, last update
-   Connections — bank authorize, reauth, T212 configuration status, sync runs
-   Settings — timezone/currency, privacy, logout, devices/sessions

Enable Banking SCA always uses the system/browser flow, not an embedded
WebView.

## Mobile

Flutter is a public OAuth client using Authorization Code + PKCE. Secure
storage is used for refresh/session material. Access tokens are
short-lived.

## Notifications

Domain event -\> notification policy -\> notification record -\> push
adapter. Push contains opaque notification ID; sensitive details are
fetched after authenticated app open.

## Consistency

Financial imports use database transactions and uniqueness constraints.
Analytics read only committed normalized data. External HTTP calls must
not be held inside long DB transactions.


## Web BFF authentication boundary

The browser never calls protected Worthly API endpoints with the
`web_session` cookie. The flow is:

```text
Browser -- Secure HttpOnly web_session --> Next.js BFF
Next.js BFF -- Authorization: Bearer <access_token> --> Worthly API
Flutter     -- Authorization: Bearer <access_token> --> Worthly API
```

`web_session` authenticates the browser to the BFF only. The BFF keeps
access/refresh tokens inside that encrypted cookie and sends Bearer to
the API. Worthly API is a Bearer-token Resource Server only. This is a
hard architecture rule, not an implementation option.
