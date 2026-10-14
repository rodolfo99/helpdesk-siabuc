CREATE TABLE policy_installments (
 id BIGSERIAL PRIMARY KEY, policy_id BIGINT NOT NULL REFERENCES support_policies(id), request_id UUID UNIQUE NOT NULL,
 due_on DATE NOT NULL, amount NUMERIC(11,2) NOT NULL CHECK(amount>0), description VARCHAR(1000) NOT NULL,
 cancelled BOOLEAN NOT NULL DEFAULT FALSE, cancel_reason TEXT, cancelled_at TIMESTAMPTZ,
 created_by BIGINT NOT NULL REFERENCES app_users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_installments_due ON policy_installments(due_on,policy_id);
CREATE TABLE installment_allocations (
 id BIGSERIAL PRIMARY KEY, installment_id BIGINT NOT NULL REFERENCES policy_installments(id), payment_id BIGINT NOT NULL REFERENCES payments(id),
 amount NUMERIC(11,2) NOT NULL CHECK(amount>0), created_by BIGINT NOT NULL REFERENCES app_users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 removed BOOLEAN NOT NULL DEFAULT FALSE, removed_reason TEXT, removed_by BIGINT REFERENCES app_users(id), removed_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_installment_payment_active ON installment_allocations(installment_id,payment_id) WHERE NOT removed;
CREATE TABLE installment_history (
 id BIGSERIAL PRIMARY KEY, installment_id BIGINT NOT NULL REFERENCES policy_installments(id), action VARCHAR(40) NOT NULL,
 description TEXT NOT NULL, created_by BIGINT NOT NULL REFERENCES app_users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE installment_settings (
 id INTEGER PRIMARY KEY CHECK(id=1), enabled BOOLEAN NOT NULL DEFAULT TRUE, days_before_due INTEGER NOT NULL DEFAULT 7 CHECK(days_before_due BETWEEN 0 AND 365)
);
INSERT INTO installment_settings(id) VALUES(1);
CREATE TABLE installment_notices (
 id BIGSERIAL PRIMARY KEY, installment_id BIGINT NOT NULL REFERENCES policy_installments(id), kind VARCHAR(12) NOT NULL CHECK(kind IN ('UPCOMING','OVERDUE')),
 due_on DATE NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), UNIQUE(installment_id,kind,due_on)
);
CREATE TABLE installment_notice_reads (
 notice_id BIGINT NOT NULL REFERENCES installment_notices(id), user_id BIGINT NOT NULL REFERENCES app_users(id), read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY(notice_id,user_id)
);
