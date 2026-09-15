-- Optional TOTP for app_user. Secret is AES-GCM via PayloadCrypto.
-- Recovery codes are SHA-256(user_id || ':' || normalized_code).

ALTER TABLE app_user
    ADD COLUMN totp_secret_encrypted bytea NULL,
    ADD COLUMN totp_pending_secret_encrypted bytea NULL,
    ADD COLUMN totp_enabled_at timestamptz NULL,
    ADD COLUMN totp_last_used_counter bigint NULL;

CREATE TABLE totp_recovery_code (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    code_hash text NOT NULL,
    used_at timestamptz NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX idx_totp_recovery_code_user ON totp_recovery_code (user_id);
