-- Append-only financial evidence. pg_dump includes the original bytes.
CREATE TABLE payment_attachments (
 id BIGSERIAL PRIMARY KEY,
 payment_id BIGINT NOT NULL REFERENCES payments(id),
 uploaded_by BIGINT NOT NULL REFERENCES app_users(id),
 request_id UUID NOT NULL,
 filename VARCHAR(180) NOT NULL,
 content_type VARCHAR(100) NOT NULL CHECK (content_type IN ('application/pdf','image/png','image/jpeg')),
 size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
 sha256 CHAR(64) NOT NULL,
 file_bytes BYTEA NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(uploaded_by,request_id),
 CHECK (octet_length(file_bytes)=size_bytes)
);
CREATE INDEX idx_payment_attachments_payment ON payment_attachments(payment_id,id DESC);
ALTER TABLE policy_history DROP CONSTRAINT policy_history_action_check;
ALTER TABLE policy_history ADD CONSTRAINT policy_history_action_check CHECK (
 action IN ('CREATE_POLICY','UPDATE_POLICY','CANCEL_POLICY','CREATE_PAYMENT','VOID_PAYMENT','ATTACH_PAYMENT_PROOF')
);
