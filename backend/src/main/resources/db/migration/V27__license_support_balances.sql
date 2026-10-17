-- Existing balances stay unassigned. Do not invent licenses or credit defaults during migration.
CREATE TABLE license_support_defaults (
 id INTEGER PRIMARY KEY CHECK (id=1),
 email_minutes BIGINT NOT NULL DEFAULT 0 CHECK (email_minutes BETWEEN 0 AND 1000000000),
 remote_minutes BIGINT NOT NULL DEFAULT 0 CHECK (remote_minutes BETWEEN 0 AND 1000000000),
 configured BOOLEAN NOT NULL DEFAULT FALSE,
 revision BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO license_support_defaults(id) VALUES (1);
CREATE TABLE support_licenses (
 id BIGSERIAL PRIMARY KEY,
 institution_id BIGINT NOT NULL REFERENCES institutions(id),
 number VARCHAR(100) NOT NULL CHECK (LENGTH(BTRIM(number))>0),
 defaults_revision BIGINT NOT NULL,
 initial_email_minutes BIGINT NOT NULL CHECK (initial_email_minutes BETWEEN 0 AND 1000000000),
 initial_remote_minutes BIGINT NOT NULL CHECK (initial_remote_minutes BETWEEN 0 AND 1000000000),
 email_allocated_minutes BIGINT NOT NULL CHECK (email_allocated_minutes BETWEEN 0 AND 1000000000),
 remote_allocated_minutes BIGINT NOT NULL CHECK (remote_allocated_minutes BETWEEN 0 AND 1000000000),
 email_used_minutes BIGINT NOT NULL DEFAULT 0 CHECK (email_used_minutes BETWEEN 0 AND email_allocated_minutes),
 remote_used_minutes BIGINT NOT NULL DEFAULT 0 CHECK (remote_used_minutes BETWEEN 0 AND remote_allocated_minutes),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(id,institution_id)
);
CREATE UNIQUE INDEX ux_support_license_number ON support_licenses(LOWER(BTRIM(number)));
CREATE INDEX idx_support_license_institution ON support_licenses(institution_id,id);
ALTER TABLE support_cases ADD COLUMN license_id BIGINT;
ALTER TABLE support_cases ADD CONSTRAINT cases_license_institution_fk
 FOREIGN KEY(license_id,institution_id) REFERENCES support_licenses(id,institution_id);
ALTER TABLE support_policies ADD COLUMN license_id BIGINT;
ALTER TABLE support_policies ADD CONSTRAINT policies_license_institution_fk
 FOREIGN KEY(license_id,institution_id) REFERENCES support_licenses(id,institution_id);
CREATE TABLE license_support_history (
 id BIGSERIAL PRIMARY KEY,
 license_id BIGINT NOT NULL REFERENCES support_licenses(id),
 kind VARCHAR(20) NOT NULL CHECK (kind IN ('INITIAL','TOPUP','PAYMENT','VOID_PAYMENT','USAGE','VOID_USAGE')),
 email_minutes BIGINT NOT NULL,
 remote_minutes BIGINT NOT NULL,
 reason TEXT NOT NULL,
 request_id UUID UNIQUE,
 payment_id BIGINT REFERENCES payments(id),
 entry_id BIGINT REFERENCES case_entries(id),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_license_support_history ON license_support_history(license_id,id DESC);
