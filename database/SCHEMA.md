# Database Schema v1

Normative constraints for Flyway. All tables use UUID PK.
Mutable tables have `created_at timestamptz not null` and
`updated_at timestamptz not null`.

Exact Flyway files are created in Phase 0/1 and tested with PostgreSQL
Testcontainers. This document is the constraint source of truth; a
migration that omits a uniqueness rule below is a spec defect.

## identity

```sql
app_user (
  id uuid pk,
  email citext not null unique,
  password_hash text not null,
  status text not null, -- ACTIVE | LOCKED | DISABLED
  reporting_timezone varchar(64) not null default 'Europe/Lisbon',
  reporting_currency char(3) not null default 'EUR',
  failed_login_count int not null default 0,
  locked_until timestamptz null,
  last_login_at timestamptz null
)

-- BFF session metadata for revocation/audit. Resource Server does not
-- authenticate HTTP cookies. token_hash is the BFF session identifier hash.
web_session (
  id uuid pk,
  user_id uuid not null references app_user(id),
  token_hash text not null unique,
  expires_at timestamptz not null,
  idle_expires_at timestamptz not null,
  last_seen_at timestamptz not null,
  revoked_at timestamptz null,
  user_agent_hash text null,
  ip_prefix text null
)

oauth_device_session (
  id uuid pk,
  user_id uuid not null references app_user(id),
  device_name text null,
  platform text not null, -- WEB | ANDROID | IOS
  refresh_family_id uuid not null,
  last_seen_at timestamptz not null,
  revoked_at timestamptz null
)

oauth_refresh_token (
  id uuid pk,
  device_session_id uuid not null references oauth_device_session(id),
  family_id uuid not null,
  token_hash text not null unique,
  parent_token_id uuid null references oauth_refresh_token(id),
  issued_at timestamptz not null,
  expires_at timestamptz not null,
  used_at timestamptz null,
  revoked_at timestamptz null
)
```

## connections

```sql
provider_connection (
  id uuid pk,
  user_id uuid not null references app_user(id),
  provider text not null, -- ENABLE_BANKING | TRADING_212
  status text not null, -- ACTIVE | REAUTH_REQUIRED | CONFIGURATION_REQUIRED | ERROR | DISABLED
  aspsp_name text null,
  aspsp_country char(2) null,
  external_session_id_encrypted bytea null,
  consent_expires_at timestamptz null,
  last_successful_sync_at timestamptz null,
  last_error_code text null,
  credentials_encrypted bytea null -- AES-GCM blob for Trading 212 API key/secret
)

-- v1: at most one Trading 212 connection per owner
create unique index uq_provider_connection_t212
  on provider_connection (user_id)
  where provider = 'TRADING_212';

authorization_attempt (
  id uuid pk,
  user_id uuid not null references app_user(id),
  provider text not null,
  state_hash text not null unique,
  redirect_target text not null,
  expires_at timestamptz not null,
  used_at timestamptz null,
  aspsp_name text null,
  aspsp_country char(2) null
)
```

Trading 212 API key/secret are encrypted in
`credentials_encrypted`. They never appear in API responses. Enable
Banking session identifiers stay on `external_session_id_encrypted`.

## banking

```sql
financial_account (
  id uuid pk,
  user_id uuid not null references app_user(id),
  connection_id uuid not null references provider_connection(id),
  provider_account_alias text not null,
  identification_hash text null,
  type text not null, -- CURRENT | SAVINGS | CARD | BROKERAGE | OTHER
  display_name text not null,
  currency char(3) not null,
  masked_identifier text null,
  active boolean not null default true
)

create unique index uq_financial_account_active_hash
  on financial_account (connection_id, identification_hash)
  where identification_hash is not null and active = true;

create unique index uq_financial_account_active_alias
  on financial_account (connection_id, provider_account_alias)
  where active = true;

balance_snapshot (
  id uuid pk,
  account_id uuid not null references financial_account(id),
  balance_type text not null,
  amount numeric(19,4) not null,
  currency char(3) not null,
  observed_at timestamptz not null,
  reference_date date null,
  source_hash text not null,
  unique (account_id, source_hash)
)

external_transaction (
  id uuid pk,
  account_id uuid not null references financial_account(id),
  provider_transaction_id text null,
  fallback_fingerprint text null,
  provider_status text not null,
  amount numeric(19,4) not null,
  currency char(3) not null,
  booked_at timestamptz null,
  value_date date null,
  description text null,
  counterparty text null,
  raw_payload_encrypted bytea null,
  raw_key_version int null,
  raw_expires_at timestamptz null
)

create unique index uq_external_tx_provider_id
  on external_transaction (account_id, provider_transaction_id)
  where provider_transaction_id is not null;

create unique index uq_external_tx_fallback
  on external_transaction (account_id, fallback_fingerprint)
  where provider_transaction_id is null and fallback_fingerprint is not null;

transaction (
  id uuid pk,
  account_id uuid not null references financial_account(id),
  external_transaction_id uuid not null unique references external_transaction(id),
  direction text not null, -- CREDIT | DEBIT
  lifecycle_status text not null, -- PENDING | BOOKED | REVERSED | UNKNOWN
  economic_type text not null,
  amount numeric(19,4) not null,
  currency char(3) not null,
  merchant text null,
  description text null,
  location text null,
  reporting_at timestamptz not null,
  category_id uuid null references category(id),
  categorization_source text not null, -- MANUAL | RULE | HEURISTIC | UNCATEGORIZED
  notes text null
)
```

## categorization / transfers / investments

```sql
category (
  id uuid pk,
  user_id uuid null references app_user(id),
  code text null,
  parent_id uuid null references category(id),
  label text not null,
  system boolean not null,
  active boolean not null default true,
  unique (code)
)

categorization_rule (
  id uuid pk,
  user_id uuid not null references app_user(id),
  priority int not null,
  field text not null, -- MERCHANT | DESCRIPTION | ACCOUNT_ID | DIRECTION
  operator text not null, -- EQUALS | CONTAINS | STARTS_WITH
  match_value text not null,
  amount_min numeric(19,4) null,
  amount_max numeric(19,4) null,
  target_category_id uuid not null references category(id),
  enabled boolean not null default true
)

transfer_match (
  id uuid pk,
  user_id uuid not null references app_user(id),
  left_transaction_id uuid not null references transaction(id),
  right_transaction_id uuid not null references transaction(id),
  confidence int not null,
  method text not null, -- AUTO | MANUAL
  status text not null, -- LINKED | SUGGESTED | REJECTED
  pair_fingerprint text not null,
  rejected_at timestamptz null,
  unique (left_transaction_id, right_transaction_id)
)

create unique index uq_transfer_match_active_pair
  on transfer_match (pair_fingerprint)
  where status in ('LINKED', 'SUGGESTED');

investment_account (
  id uuid pk,
  user_id uuid not null references app_user(id),
  provider_connection_id uuid not null references provider_connection(id),
  provider_account_id text not null,
  currency char(3) not null,
  unique (provider_connection_id, provider_account_id)
)

position_snapshot (
  id uuid pk,
  investment_account_id uuid not null references investment_account(id),
  instrument_key text not null,
  ticker text null,
  quantity numeric(28,10) not null,
  average_price numeric(19,6) null,
  market_value numeric(19,4) null,
  currency char(3) not null,
  observed_at timestamptz not null
)

investment_event (
  id uuid pk,
  investment_account_id uuid not null references investment_account(id),
  provider_event_id text not null,
  event_type text not null,
  amount numeric(19,4) null,
  currency char(3) null,
  occurred_at timestamptz not null,
  instrument_key text null,
  quantity numeric(28,10) null,
  unique (investment_account_id, provider_event_id)
)
```

## sync / audit / notification

```sql
sync_run (
  id uuid pk,
  connection_id uuid not null references provider_connection(id),
  trigger_type text not null, -- SCHEDULED | MANUAL
  status text not null, -- QUEUED | RUNNING | SUCCEEDED | FAILED | RATE_LIMITED
  started_at timestamptz not null,
  finished_at timestamptz null,
  imported_count int null,
  updated_count int null,
  correlation_id text not null,
  error_code text null,
  next_retry_at timestamptz null
)

audit_event (
  id uuid pk,
  user_id uuid null references app_user(id),
  event_type text not null,
  occurred_at timestamptz not null,
  correlation_id text not null,
  safe_metadata jsonb not null default '{}'
)
-- append-only: no updates, no deletes from application code

notification (
  id uuid pk,
  user_id uuid not null references app_user(id),
  type text not null,
  status text not null,
  title_key text not null,
  body_key text not null,
  created_at timestamptz not null,
  read_at timestamptz null
)

push_device (
  id uuid pk,
  device_session_id uuid not null references oauth_device_session(id),
  provider text not null, -- FCM | APNS
  token_encrypted bytea not null,
  token_key_version int not null,
  active boolean not null default true,
  last_seen_at timestamptz not null
)
```

## indexes (query)

- `transaction (account_id, reporting_at desc)`
- `transaction (reporting_at desc, economic_type)`
- `transaction (category_id, reporting_at desc)`
- `sync_run (connection_id, started_at desc)`
- `balance_snapshot (account_id, observed_at desc)`
- `external_transaction (account_id, booked_at desc)`
- `position_snapshot (investment_account_id, observed_at desc)`

## disconnect vs purge

`DELETE /connections/{id}` (disconnect): set `provider_connection.status =
DISABLED`; cancel queued sync; revoke Enable Banking session if the
provider allows. **Keep** accounts, transactions, matches, snapshots.

`POST /connections/{id}/purge` with `confirm=true`:

1. delete `transfer_match` rows whose left or right transaction belongs
   to this connection;
2. delete `transaction` then `external_transaction` for those accounts
   (encrypted payloads included);
3. delete `balance_snapshot` and `financial_account`;
4. for Trading 212, delete `investment_event`, `position_snapshot`,
   `investment_account`;
5. keep `sync_run`, `audit_event`, and the `provider_connection` row
   (`DISABLED` or `CONFIGURATION_REQUIRED`).

Application code never deletes `audit_event`.
