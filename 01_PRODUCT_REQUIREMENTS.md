# Product Requirements

## Product goal

Worthly consolidates owned bank accounts and investments into one
private financial view. It answers: current cash, investments, net
worth, monthly income/expenses, invested amount, savings rate, category
distribution, recent movements and connection health.

## Actors

-   **Owner:** the only human user in v1.
-   **Worthly Scheduler:** background synchronization.
-   **Enable Banking:** AIS aggregator for Santander Portugal and
    Revolut.
-   **Trading 212:** brokerage data provider.
-   **Push provider:** FCM/APNs transport only; never source of truth.

## Functional requirements

### Identity

-   **FR-000** Owner preferences are persisted in `app_user`.
    `reporting_timezone` defaults to `Europe/Lisbon` and
    `reporting_currency` defaults to `EUR`.
-   **FR-001** No anonymous financial endpoint.
-   **FR-002** Exactly one owner is supported in v1.
-   **FR-003** No public registration endpoint exists.
-   **FR-004** Owner can view and revoke active mobile/device sessions.
-   **FR-005** Web logout invalidates its server session.
-   **FR-006** Mobile uses Authorization Code + PKCE and refresh-token
    rotation.
-   **FR-007** Authentication failures are rate-limited and audited.
-   **FR-008** Owner can update reporting timezone and reporting currency
    through `PATCH /me`; timezone MUST be a valid IANA ZoneId and currency
    MUST be an uppercase ISO 4217 code.

### Connections

-   **FR-010** List provider connections with ACTIVE, REAUTH_REQUIRED,
    CONFIGURATION_REQUIRED, ERROR, DISABLED.
-   **FR-017** Disconnect (`DELETE /connections/{id}`) stops sync and
    retains history. Purge (`POST /connections/{id}/purge`) deletes
    imported provider data after `confirm=true` and retains audit rows.
-   **FR-011** Start Enable Banking authorization for a selected ASPSP.
-   **FR-012** Complete callback only when state is valid, unexpired and
    owner-bound.
-   **FR-013** Disconnect and purge follow FR-017.
-   **FR-014** Trading 212 credentials are configured server-side, not
    entered into browser/mobile.
-   **FR-016** At startup/configuration reconciliation, presence of a
    valid server-side Trading 212 API key + secret MUST create or upsert
    exactly one owner `TRADING_212` provider connection. Provider secrets
    are never stored in `provider_connection`. Missing/invalid
    configuration yields `CONFIGURATION_REQUIRED`; it does not silently
    delete historical connection/data.
-   **FR-015** Manual sync request is asynchronous and returns a
    sync-run resource.

### Accounts and balances

-   **FR-020** Normalize provider accounts.
-   **FR-021** Preserve a stable local account identity across Enable
    Banking reauthorization using `identification_hash` when available.
-   **FR-022** Store balance snapshots with type, amount, currency,
    source and observation time.
-   **FR-023** Mask account identifiers in API/UI.
-   **FR-024** Account `type` is CURRENT, SAVINGS, CARD, BROKERAGE or
    OTHER. Liquid-cash and net-worth inclusion follow
    `domain/CALCULATIONS.md`.

### Transactions

-   **FR-030** Import booked and pending transactions when available.
-   **FR-031** Synchronization is idempotent.
-   **FR-032** Pending-to-booked transition must not double count.
-   **FR-033** `GET /transactions` supports filters for
    date/account/category/direction/lifecycle status/economic type/text
    and amount range. Amount filters use the transaction's original
    currency; cross-currency comparison/conversion is never implicit.
-   **FR-034** Manual category and notes do not mutate provider raw
    data.
-   **FR-035** Export normalized transactions as CSV with the column set
    documented on `GET /exports/transactions.csv`, using the same
    filters as the transaction list.
-   **FR-036** Provider reversals/refunds are represented explicitly.

### Categories

-   **FR-040** Stable system taxonomy exists.
-   **FR-041** Owner can create custom categories.
-   **FR-042** Rules are ordered by priority.
-   **FR-043** Manual category overrides all automatic rules.
-   **FR-044** Rules match MERCHANT, DESCRIPTION, ACCOUNT_ID or
    DIRECTION with EQUALS, CONTAINS or STARTS_WITH, plus optional
    original-currency amount range. No regex in v1.

### Transfers

-   **FR-050** Detect candidate transfers between owned accounts.
-   **FR-051** High-confidence matches may be auto-linked but are
    reversible.
-   **FR-052** Medium-confidence matches require confirmation.
-   **FR-053** Internal transfers are excluded from income/expense.
-   **FR-054** Funding Trading 212 is classified as investment funding,
    not consumption expense.

### Investments

-   **FR-060** Import Trading 212 account summary, positions and
    available history.
-   **FR-061** V1 is read-only: no order endpoints in Worthly.
-   **FR-062** Portfolio values retain provider currency and observation
    time.
-   **FR-063** Dividends, interest, fees, deposits and withdrawals are
    normalized as investment events.

### Analytics

-   **FR-070** Return cash, investment value and net worth via
    `GET /analytics/summary`, grouped by currency.
-   **FR-071** Return monthly income, expenses, investment funding and
    savings rate.
-   **FR-072** Reporting period is calculated in owner reporting
    timezone.
-   **FR-073** Cross-currency totals require an explicit FX rate source;
    until FX is implemented, analytics and investment aggregate DTOs
    return independent totals grouped by ISO 4217 currency. No endpoint
    exposes a single cross-currency `Money` total.
-   **FR-074** A savings rate is calculated independently per currency.
    A global savings rate is absent until an explicit FX subsystem exists.

### Notifications

-   **FR-080** Reauthorization-required and repeated-sync-failure
    notifications.
-   **FR-081** Optional insights/milestones.
-   **FR-082** Push payloads contain no full account numbers or provider
    credentials.

### Privacy/data lifecycle

-   **FR-090** Disconnect provider.
-   **FR-091** Delete locally imported provider data after explicit
    confirmation.
-   **FR-092** Export data.
-   **FR-093** Audit security-sensitive operations.

## Explicitly out of scope v1

Payment initiation, card issuing, brokerage orders, public
SaaS/multi-user, credit decisions, bank scraping, financial advice,
crypto wallets, tax filing and guaranteed real-time card events.

## Non-functional requirements

-   **NFR-001 Security:** target OWASP ASVS Level 2 and OWASP MASVS
    controls applicable to mobile.
-   **NFR-002 Integrity:** no floating-point money.
-   **NFR-003 Availability:** 99% target excluding upstream
    outages/maintenance.
-   **NFR-004 Query performance:** transaction list p95 \< 1s at 100k
    local transactions.
-   **NFR-005 Cached dashboard:** p95 backend \< 500ms.
-   **NFR-006 Time:** instants UTC; reporting periods owner timezone.
-   **NFR-007 Auditability:** correlation IDs and append-oriented audit
    records.
-   **NFR-008 Recoverability:** encrypted backups and tested restore.
-   **NFR-009 Portability:** Docker Compose; push transport is the
    deliberate external exception for native mobile delivery.
-   **NFR-010 Accessibility:** Web WCAG-oriented semantics; mobile
    dynamic text/screen reader support.

## MVP acceptance

MVP is complete only when Santander and Revolut can be authorized
through Enable Banking, Trading 212 can be read with read-only
credentials, imports are duplicate-safe, formulas reconcile against
fixtures, transfer matching avoids double counting, Web works, Mobile
works, sessions can be revoked and a backup restore has been tested.
