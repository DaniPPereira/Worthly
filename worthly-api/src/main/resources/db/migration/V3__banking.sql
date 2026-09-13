-- Phase 1 banking/connections. Source: database/SCHEMA.md

CREATE TABLE provider_connection (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    provider text NOT NULL,
    status text NOT NULL,
    aspsp_name text NULL,
    aspsp_country char(2) NULL,
    external_session_id_encrypted bytea NULL,
    consent_expires_at timestamptz NULL,
    last_successful_sync_at timestamptz NULL,
    last_error_code text NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_provider_connection_provider CHECK (provider IN ('ENABLE_BANKING', 'TRADING_212')),
    CONSTRAINT chk_provider_connection_status CHECK (status IN (
        'ACTIVE', 'REAUTH_REQUIRED', 'CONFIGURATION_REQUIRED', 'ERROR', 'DISABLED'
    ))
);

CREATE UNIQUE INDEX uq_provider_connection_t212
    ON provider_connection (user_id)
    WHERE provider = 'TRADING_212';

CREATE TABLE authorization_attempt (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    provider text NOT NULL,
    state_hash text NOT NULL UNIQUE,
    redirect_target text NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz NULL,
    aspsp_name text NULL,
    aspsp_country char(2) NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_authorization_attempt_redirect CHECK (redirect_target IN ('WEB', 'MOBILE'))
);

CREATE TABLE category (
    id uuid PRIMARY KEY,
    user_id uuid NULL REFERENCES app_user (id),
    code text NULL,
    parent_id uuid NULL REFERENCES category (id),
    label text NOT NULL,
    system boolean NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (code)
);

CREATE TABLE financial_account (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    connection_id uuid NOT NULL REFERENCES provider_connection (id),
    provider_account_alias text NOT NULL,
    identification_hash text NULL,
    type text NOT NULL,
    display_name text NOT NULL,
    currency char(3) NOT NULL,
    masked_identifier text NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_financial_account_type CHECK (type IN ('CURRENT', 'SAVINGS', 'CARD', 'BROKERAGE', 'OTHER')),
    CONSTRAINT chk_financial_account_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX uq_financial_account_active_hash
    ON financial_account (connection_id, identification_hash)
    WHERE identification_hash IS NOT NULL AND active = true;

CREATE UNIQUE INDEX uq_financial_account_active_alias
    ON financial_account (connection_id, provider_account_alias)
    WHERE active = true;

CREATE TABLE balance_snapshot (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES financial_account (id),
    balance_type text NOT NULL,
    amount numeric(19, 4) NOT NULL,
    currency char(3) NOT NULL,
    observed_at timestamptz NOT NULL,
    reference_date date NULL,
    source_hash text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (account_id, source_hash),
    CONSTRAINT chk_balance_snapshot_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_balance_snapshot_account_observed
    ON balance_snapshot (account_id, observed_at DESC);

CREATE TABLE external_transaction (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES financial_account (id),
    provider_transaction_id text NULL,
    fallback_fingerprint text NULL,
    provider_status text NOT NULL,
    amount numeric(19, 4) NOT NULL,
    currency char(3) NOT NULL,
    booked_at timestamptz NULL,
    value_date date NULL,
    description text NULL,
    counterparty text NULL,
    raw_payload_encrypted bytea NULL,
    raw_key_version int NULL,
    raw_expires_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_external_tx_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX uq_external_tx_provider_id
    ON external_transaction (account_id, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

CREATE UNIQUE INDEX uq_external_tx_fallback
    ON external_transaction (account_id, fallback_fingerprint)
    WHERE provider_transaction_id IS NULL AND fallback_fingerprint IS NOT NULL;

CREATE INDEX idx_external_tx_account_booked
    ON external_transaction (account_id, booked_at DESC);

CREATE TABLE transaction (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES financial_account (id),
    external_transaction_id uuid NOT NULL UNIQUE REFERENCES external_transaction (id),
    direction text NOT NULL,
    lifecycle_status text NOT NULL,
    economic_type text NOT NULL,
    amount numeric(19, 4) NOT NULL,
    currency char(3) NOT NULL,
    merchant text NULL,
    description text NULL,
    reporting_at timestamptz NOT NULL,
    category_id uuid NULL REFERENCES category (id),
    categorization_source text NOT NULL,
    notes text NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_transaction_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_transaction_lifecycle CHECK (lifecycle_status IN ('PENDING', 'BOOKED', 'REVERSED', 'UNKNOWN')),
    CONSTRAINT chk_transaction_economic CHECK (economic_type IN (
        'INCOME', 'EXPENSE', 'INTERNAL_TRANSFER', 'INVESTMENT_FUNDING',
        'INVESTMENT_WITHDRAWAL', 'REFUND', 'FEE', 'OTHER'
    )),
    CONSTRAINT chk_transaction_categorization CHECK (categorization_source IN (
        'MANUAL', 'RULE', 'HEURISTIC', 'UNCATEGORIZED'
    )),
    CONSTRAINT chk_transaction_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_transaction_account_reporting
    ON transaction (account_id, reporting_at DESC);

CREATE TABLE sync_run (
    id uuid PRIMARY KEY,
    connection_id uuid NOT NULL REFERENCES provider_connection (id),
    trigger_type text NOT NULL,
    status text NOT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz NULL,
    imported_count int NULL,
    updated_count int NULL,
    correlation_id text NOT NULL,
    error_code text NULL,
    next_retry_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_sync_run_trigger CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT chk_sync_run_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RATE_LIMITED'))
);

CREATE INDEX idx_sync_run_connection_started
    ON sync_run (connection_id, started_at DESC);
