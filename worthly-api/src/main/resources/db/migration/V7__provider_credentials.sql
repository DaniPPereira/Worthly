-- Encrypted per-connection provider credentials (Trading 212 API key/secret).
-- Distinct from Enable Banking session blobs on external_session_id_encrypted.

ALTER TABLE provider_connection
    ADD COLUMN credentials_encrypted bytea NULL;
