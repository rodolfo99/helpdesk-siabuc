CREATE TABLE client_finance_access (
 institution_id BIGINT PRIMARY KEY REFERENCES institutions(id),
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 updated_by BIGINT NOT NULL REFERENCES app_users(id),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE payment_requests (
 id BIGSERIAL PRIMARY KEY,
 submission_id UUID NOT NULL,
 payment_request_id UUID UNIQUE NOT NULL,
 institution_id BIGINT NOT NULL REFERENCES institutions(id),
 policy_id BIGINT REFERENCES support_policies(id),
 reference VARCHAR(100) NOT NULL CHECK(length(btrim(reference))>0),
 amount NUMERIC(11,2) NOT NULL CHECK(amount>=0 AND amount<=999999999.99),
 currency VARCHAR(3) NOT NULL DEFAULT 'MXN' CHECK(currency='MXN'),
 paid_on DATE NOT NULL,
 method VARCHAR(20) NOT NULL CHECK(method IN ('TRANSFER','DEPOSIT','CASH','CREDIT_CARD','DEBIT_CARD','CHECK','OTHER','CARD')),
 email_minutes BIGINT NOT NULL CHECK(email_minutes BETWEEN 0 AND 1000000000),
 remote_minutes BIGINT NOT NULL CHECK(remote_minutes BETWEEN 0 AND 1000000000),
 description TEXT NOT NULL CHECK(length(btrim(description)) BETWEEN 1 AND 8000),
 status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 submitted_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 decided_by BIGINT REFERENCES app_users(id),
 decided_at TIMESTAMPTZ,
 decision_reason TEXT,
 payment_id BIGINT UNIQUE REFERENCES payments(id),
 UNIQUE(submitted_by,submission_id),
 FOREIGN KEY(policy_id,institution_id) REFERENCES support_policies(id,institution_id),
 CHECK((policy_id IS NULL AND amount>0 AND (email_minutes>0 OR remote_minutes>0)) OR (policy_id IS NOT NULL AND (amount>0 OR email_minutes>0 OR remote_minutes>0))),
 CHECK((status='PENDING' AND decided_by IS NULL AND decided_at IS NULL AND decision_reason IS NULL AND payment_id IS NULL)
    OR(status='APPROVED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND decision_reason IS NOT NULL AND payment_id IS NOT NULL)
    OR(status='REJECTED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND decision_reason IS NOT NULL AND length(btrim(decision_reason))>0 AND payment_id IS NULL))
);
CREATE INDEX idx_payment_requests_institution ON payment_requests(institution_id,id DESC);
CREATE INDEX idx_payment_requests_status ON payment_requests(status,id DESC);
CREATE TABLE payment_request_attachments (
 id BIGSERIAL PRIMARY KEY,
 request_id BIGINT NOT NULL REFERENCES payment_requests(id),
 uploaded_by BIGINT NOT NULL REFERENCES app_users(id),
 submission_id UUID NOT NULL,
 filename VARCHAR(180) NOT NULL,
 content_type VARCHAR(100) NOT NULL CHECK(content_type IN ('application/pdf','image/png','image/jpeg')),
 size_bytes BIGINT NOT NULL CHECK(size_bytes BETWEEN 1 AND 10485760),
 sha256 CHAR(64) NOT NULL,
 file_bytes BYTEA NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(uploaded_by,submission_id),
 CHECK(octet_length(file_bytes)=size_bytes)
);
CREATE INDEX idx_payment_request_attachments ON payment_request_attachments(request_id,id);
