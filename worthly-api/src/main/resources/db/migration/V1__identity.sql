-- Worthly identity schema (Phase 0). Source: database/SCHEMA.md

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    email citext NOT NULL UNIQUE,
    password_hash text NOT NULL,
    status text NOT NULL,
    reporting_timezone varchar(64) NOT NULL DEFAULT 'Europe/Lisbon',
    reporting_currency char(3) NOT NULL DEFAULT 'EUR',
    failed_login_count int NOT NULL DEFAULT 0,
    locked_until timestamptz NULL,
    last_login_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT chk_app_user_currency CHECK (reporting_currency ~ '^[A-Z]{3}$')
);

CREATE TABLE web_session (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    idle_expires_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    revoked_at timestamptz NULL,
    user_agent_hash text NULL,
    ip_prefix text NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE oauth_device_session (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    device_name text NULL,
    platform text NOT NULL,
    refresh_family_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    revoked_at timestamptz NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_oauth_device_platform CHECK (platform IN ('WEB', 'ANDROID', 'IOS'))
);

CREATE INDEX idx_oauth_device_session_user ON oauth_device_session (user_id);

CREATE TABLE oauth_refresh_token (
    id uuid PRIMARY KEY,
    device_session_id uuid NOT NULL REFERENCES oauth_device_session (id),
    family_id uuid NOT NULL,
    token_hash text NOT NULL UNIQUE,
    parent_token_id uuid NULL REFERENCES oauth_refresh_token (id),
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz NULL,
    revoked_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX idx_oauth_refresh_token_family ON oauth_refresh_token (family_id);

CREATE TABLE audit_event (
    id uuid PRIMARY KEY,
    user_id uuid NULL REFERENCES app_user (id),
    event_type text NOT NULL,
    occurred_at timestamptz NOT NULL,
    correlation_id text NOT NULL,
    safe_metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_audit_event_occurred ON audit_event (occurred_at DESC);
