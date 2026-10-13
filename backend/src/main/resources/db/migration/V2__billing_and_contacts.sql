ALTER TABLE app_users ADD COLUMN email VARCHAR(254) NOT NULL DEFAULT '';
ALTER TABLE app_users DROP CONSTRAINT app_users_role_check;
ALTER TABLE app_users DROP CONSTRAINT app_users_check;
ALTER TABLE app_users ADD CONSTRAINT app_users_role_check CHECK (role IN ('ADMIN','USER','BILLING'));
ALTER TABLE app_users ADD CONSTRAINT app_users_scope_check CHECK (
 (role IN ('ADMIN','BILLING') AND institution_id IS NULL) OR (role='USER' AND institution_id IS NOT NULL)
);
ALTER TABLE institutions ADD COLUMN support_admin_id BIGINT REFERENCES app_users(id);
ALTER TABLE support_cases ADD COLUMN assigned_admin_id BIGINT REFERENCES app_users(id);
CREATE INDEX idx_cases_assigned_admin ON support_cases(assigned_admin_id);

CREATE TABLE payments (
 id BIGSERIAL PRIMARY KEY,
 request_id UUID UNIQUE NOT NULL,
 institution_id BIGINT NOT NULL REFERENCES institutions(id),
 reference VARCHAR(100) NOT NULL CHECK (LENGTH(BTRIM(reference))>0),
 amount NUMERIC(11,2) NOT NULL CHECK (amount>0 AND amount<=999999999.99),
 currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
 paid_on DATE NOT NULL,
 method VARCHAR(10) NOT NULL CHECK (method IN ('TRANSFER','CASH','CARD','OTHER')),
 email_minutes BIGINT NOT NULL CHECK (email_minutes BETWEEN 0 AND 1000000000),
 remote_minutes BIGINT NOT NULL CHECK (remote_minutes BETWEEN 0 AND 1000000000),
 notes TEXT NOT NULL CHECK (LENGTH(BTRIM(notes))>0),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 voided BOOLEAN NOT NULL DEFAULT FALSE,
 void_reason TEXT,
 voided_by BIGINT REFERENCES app_users(id),
 voided_at TIMESTAMPTZ,
 CHECK (email_minutes>0 OR remote_minutes>0),
 CHECK ((NOT voided AND void_reason IS NULL AND voided_by IS NULL AND voided_at IS NULL)
     OR (voided AND void_reason IS NOT NULL AND voided_by IS NOT NULL AND voided_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_payment_institution_reference ON payments(institution_id,LOWER(BTRIM(reference)));
CREATE INDEX idx_payments_institution_created ON payments(institution_id,created_at DESC);
