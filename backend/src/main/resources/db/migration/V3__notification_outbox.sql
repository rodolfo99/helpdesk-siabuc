-- Durable outbox: committed together with support/payment events. Delivery is at least once.
CREATE TABLE notification_outbox (
 id BIGSERIAL PRIMARY KEY,
 event_key UUID NOT NULL,
 case_id BIGINT REFERENCES support_cases(id),
 payment_id BIGINT REFERENCES payments(id),
 event_type VARCHAR(60) NOT NULL,
 recipient_type VARCHAR(20) NOT NULL CHECK (recipient_type IN ('INSTITUTION','USER')),
 recipient_entity_id BIGINT,
 recipient VARCHAR(254) NOT NULL DEFAULT '',
 subject VARCHAR(300) NOT NULL,
 body TEXT NOT NULL,
 internal BOOLEAN NOT NULL DEFAULT FALSE,
 status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','SENDING','SENT','FAILED','SKIPPED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
 last_error VARCHAR(500),
 available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 claim_token UUID,
 claimed_until TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 sent_at TIMESTAMPTZ,
 CHECK (NOT internal OR recipient_type='USER'),
 CHECK (NOT (case_id IS NOT NULL AND payment_id IS NOT NULL)),
 CHECK ((status='SENDING' AND claim_token IS NOT NULL AND claimed_until IS NOT NULL)
     OR (status<>'SENDING' AND claim_token IS NULL AND claimed_until IS NULL))
);
CREATE INDEX idx_notification_ready ON notification_outbox(status,available_at,id);
CREATE INDEX idx_notification_lease ON notification_outbox(claimed_until) WHERE status='SENDING';
CREATE UNIQUE INDEX uq_notification_event_recipient ON notification_outbox(event_key,LOWER(recipient)) WHERE recipient<>'';
