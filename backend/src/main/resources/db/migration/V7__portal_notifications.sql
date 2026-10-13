ALTER TABLE support_cases ADD COLUMN waiting_for VARCHAR(10) NOT NULL DEFAULT 'ADMIN'
 CHECK (waiting_for IN ('ADMIN','CLIENT','NONE'));
UPDATE support_cases SET waiting_for='NONE' WHERE status='CLOSED';

CREATE TABLE portal_notifications (
 id BIGSERIAL PRIMARY KEY,
 recipient_id BIGINT NOT NULL REFERENCES app_users(id),
 institution_id BIGINT NOT NULL REFERENCES institutions(id),
 case_id BIGINT REFERENCES support_cases(id),
 event_type VARCHAR(50) NOT NULL,
 title VARCHAR(300) NOT NULL,
 description TEXT NOT NULL,
 internal BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 read_at TIMESTAMPTZ,
 CHECK (NOT internal OR case_id IS NOT NULL)
);
CREATE INDEX portal_notifications_recipient_idx ON portal_notifications(recipient_id,created_at DESC,id DESC);
CREATE INDEX portal_notifications_unread_idx ON portal_notifications(recipient_id) WHERE read_at IS NULL;

CREATE TABLE balance_alert_settings (
 institution_id BIGINT PRIMARY KEY REFERENCES institutions(id),
 email_threshold_minutes BIGINT NOT NULL DEFAULT 60 CHECK (email_threshold_minutes BETWEEN 0 AND 1000000000),
 remote_threshold_minutes BIGINT NOT NULL DEFAULT 30 CHECK (remote_threshold_minutes BETWEEN 0 AND 1000000000),
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 email_low BOOLEAN NOT NULL DEFAULT FALSE,
 remote_low BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
