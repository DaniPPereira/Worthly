-- Phase 2 notifications (connection health / sync reminders). Source: database/SCHEMA.md

CREATE TABLE notification (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    type text NOT NULL,
    status text NOT NULL,
    title_key text NOT NULL,
    body_key text NOT NULL,
    created_at timestamptz NOT NULL,
    read_at timestamptz NULL,
    CONSTRAINT chk_notification_type CHECK (type IN (
        'CONNECTION_REAUTH_REQUIRED', 'SYNC_REPEATED_FAILURE',
        'NET_WORTH_MILESTONE', 'SPENDING_INSIGHT', 'CONFIGURATION_REQUIRED'
    )),
    CONSTRAINT chk_notification_status CHECK (status IN ('UNREAD', 'READ'))
);

CREATE INDEX idx_notification_user_created
    ON notification (user_id, created_at DESC);
