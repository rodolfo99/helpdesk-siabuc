-- Support agreements describe contracted service; only payment movements credit institution balances.
CREATE TABLE support_policies (
 id BIGSERIAL PRIMARY KEY,
 institution_id BIGINT NOT NULL REFERENCES institutions(id),
 folio VARCHAR(100) NOT NULL CHECK (LENGTH(BTRIM(folio))>0),
 description TEXT NOT NULL CHECK (LENGTH(BTRIM(description)) BETWEEN 1 AND 10000),
 valid_from DATE NOT NULL,
 valid_until DATE NOT NULL CHECK (valid_until>=valid_from),
 agreed_amount NUMERIC(11,2) NOT NULL CHECK (agreed_amount>0 AND agreed_amount<=999999999.99),
 currency VARCHAR(3) NOT NULL DEFAULT 'MXN' CHECK (currency='MXN'),
 email_minutes BIGINT NOT NULL CHECK (email_minutes BETWEEN 0 AND 1000000000),
 remote_minutes BIGINT NOT NULL CHECK (remote_minutes BETWEEN 0 AND 1000000000),
 status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CANCELLED')),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 cancel_reason TEXT,
 cancelled_by BIGINT REFERENCES app_users(id),
 cancelled_at TIMESTAMPTZ,
 UNIQUE(id,institution_id),
 CHECK (email_minutes>0 OR remote_minutes>0),
 CHECK ((status='ACTIVE' AND cancel_reason IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL)
     OR (status='CANCELLED' AND cancel_reason IS NOT NULL AND LENGTH(BTRIM(cancel_reason))>0 AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_policy_folio ON support_policies(LOWER(BTRIM(folio)));
CREATE INDEX idx_policies_institution_created ON support_policies(institution_id,created_at DESC,id DESC);
ALTER TABLE payments ADD COLUMN policy_id BIGINT REFERENCES support_policies(id);
ALTER TABLE payments ADD CONSTRAINT payments_policy_institution_fk FOREIGN KEY(policy_id,institution_id) REFERENCES support_policies(id,institution_id);
ALTER TABLE payments ADD CONSTRAINT payments_policy_currency_check CHECK (policy_id IS NULL OR currency='MXN');
CREATE INDEX idx_payments_policy ON payments(policy_id,id);
-- Preserve existing currencies/methods, including CARD. New API writes are restricted to MXN.
ALTER TABLE payments ALTER COLUMN method TYPE VARCHAR(20);
ALTER TABLE payments DROP CONSTRAINT payments_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_method_check
 CHECK (method IN ('TRANSFER','DEPOSIT','CASH','CREDIT_CARD','DEBIT_CARD','CHECK','OTHER','CARD'));
ALTER TABLE payments DROP CONSTRAINT payments_amount_check;
ALTER TABLE payments ADD CONSTRAINT payments_amount_check CHECK (amount>=0 AND amount<=999999999.99);
ALTER TABLE payments DROP CONSTRAINT payments_check;
ALTER TABLE payments ADD CONSTRAINT payments_value_check CHECK (
 (policy_id IS NULL AND amount>0 AND (email_minutes>0 OR remote_minutes>0))
 OR (policy_id IS NOT NULL AND (amount>0 OR email_minutes>0 OR remote_minutes>0))
);
CREATE TABLE policy_history (
 id BIGSERIAL PRIMARY KEY,
 policy_id BIGINT NOT NULL REFERENCES support_policies(id),
 action VARCHAR(40) NOT NULL CHECK (action IN ('CREATE_POLICY','UPDATE_POLICY','CANCEL_POLICY','CREATE_PAYMENT','VOID_PAYMENT')),
 description TEXT NOT NULL,
 payment_id BIGINT REFERENCES payments(id),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_policy_history_policy ON policy_history(policy_id,id DESC);
