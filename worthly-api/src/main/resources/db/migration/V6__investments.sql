-- Phase 4: Trading 212 investment tables. Source: database/SCHEMA.md

CREATE TABLE investment_account (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    provider_connection_id uuid NOT NULL REFERENCES provider_connection (id),
    provider_account_id text NOT NULL,
    currency char(3) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (provider_connection_id, provider_account_id),
    CONSTRAINT chk_investment_account_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE position_snapshot (
    id uuid PRIMARY KEY,
    investment_account_id uuid NOT NULL REFERENCES investment_account (id),
    instrument_key text NOT NULL,
    ticker text NULL,
    quantity numeric(28, 10) NOT NULL,
    average_price numeric(19, 6) NULL,
    market_value numeric(19, 4) NULL,
    currency char(3) NOT NULL,
    observed_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX idx_position_snapshot_account_observed
    ON position_snapshot (investment_account_id, observed_at DESC);

CREATE TABLE investment_event (
    id uuid PRIMARY KEY,
    investment_account_id uuid NOT NULL REFERENCES investment_account (id),
    provider_event_id text NOT NULL,
    event_type text NOT NULL,
    amount numeric(19, 4) NULL,
    currency char(3) NULL,
    occurred_at timestamptz NOT NULL,
    instrument_key text NULL,
    quantity numeric(28, 10) NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (investment_account_id, provider_event_id)
);
