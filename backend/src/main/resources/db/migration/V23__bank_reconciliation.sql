-- Reconciliation only records evidence; it never writes payments or support balances.
CREATE TABLE bank_imports (
 id BIGSERIAL PRIMARY KEY,
 account_label VARCHAR(100) NOT NULL,
 account_key VARCHAR(100) NOT NULL,
 content_hash VARCHAR(64) NOT NULL UNIQUE,
 filename VARCHAR(200) NOT NULL,
 row_count INTEGER NOT NULL CHECK (row_count BETWEEN 1 AND 1000),
 new_count INTEGER NOT NULL CHECK (new_count>=0 AND new_count<=row_count),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE bank_movements (
 id BIGSERIAL PRIMARY KEY,
 first_import_id BIGINT NOT NULL REFERENCES bank_imports(id),
 account_label VARCHAR(100) NOT NULL,
 account_key VARCHAR(100) NOT NULL,
 movement_hash VARCHAR(64) NOT NULL UNIQUE,
 booked_on DATE NOT NULL,
 reference VARCHAR(200) NOT NULL,
 reference_key VARCHAR(200) NOT NULL,
 amount NUMERIC(11,2) NOT NULL CHECK(amount>0 AND amount<=999999999.99),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 matched_payment_id BIGINT REFERENCES payments(id),
 matched_by BIGINT REFERENCES app_users(id),
 matched_at TIMESTAMPTZ,
 CHECK ((matched_payment_id IS NULL AND matched_by IS NULL AND matched_at IS NULL)
     OR (matched_payment_id IS NOT NULL AND matched_by IS NOT NULL AND matched_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_bank_movement_payment ON bank_movements(matched_payment_id) WHERE matched_payment_id IS NOT NULL;
CREATE INDEX idx_bank_movements_date ON bank_movements(booked_on DESC,id DESC);
CREATE TABLE bank_import_rows (
 import_id BIGINT NOT NULL REFERENCES bank_imports(id),
 row_number INTEGER NOT NULL,
 movement_id BIGINT NOT NULL REFERENCES bank_movements(id),
 PRIMARY KEY(import_id,row_number),
 UNIQUE(import_id,movement_id)
);
CREATE TABLE bank_reconciliation_events (
 id BIGSERIAL PRIMARY KEY,
 movement_id BIGINT NOT NULL REFERENCES bank_movements(id),
 payment_id BIGINT NOT NULL REFERENCES payments(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('MATCH','UNMATCH')),
 reason VARCHAR(2000) NOT NULL CHECK(LENGTH(BTRIM(reason))>=5),
 actor_id BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bank_reconciliation_events ON bank_reconciliation_events(movement_id,id);
