CREATE TABLE institutions (
 id BIGSERIAL PRIMARY KEY,
 name VARCHAR(180) NOT NULL,
 contact_name VARCHAR(180) NOT NULL DEFAULT '',
 email VARCHAR(254) NOT NULL DEFAULT '',
 phone VARCHAR(60) NOT NULL DEFAULT '',
 notes TEXT NOT NULL DEFAULT '',
 active BOOLEAN NOT NULL DEFAULT TRUE,
 email_allocated_minutes BIGINT NOT NULL DEFAULT 0 CHECK (email_allocated_minutes BETWEEN 0 AND 1000000000),
 email_used_minutes BIGINT NOT NULL DEFAULT 0 CHECK (email_used_minutes >= 0 AND email_used_minutes <= email_allocated_minutes),
 remote_allocated_minutes BIGINT NOT NULL DEFAULT 0 CHECK (remote_allocated_minutes BETWEEN 0 AND 1000000000),
 remote_used_minutes BIGINT NOT NULL DEFAULT 0 CHECK (remote_used_minutes >= 0 AND remote_used_minutes <= remote_allocated_minutes),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE app_users (
 id BIGSERIAL PRIMARY KEY,
 username VARCHAR(80) UNIQUE NOT NULL,
 display_name VARCHAR(180) NOT NULL,
 password_hash VARCHAR(100) NOT NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 role VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN','USER')),
 institution_id BIGINT REFERENCES institutions(id),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CHECK ((role = 'ADMIN' AND institution_id IS NULL) OR (role = 'USER' AND institution_id IS NOT NULL))
);
CREATE TABLE support_cases (
 id BIGSERIAL PRIMARY KEY,
 institution_id BIGINT NOT NULL REFERENCES institutions(id),
 title VARCHAR(220) NOT NULL,
 description TEXT NOT NULL,
 channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','REMOTE')),
 status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE case_entries (
 id BIGSERIAL PRIMARY KEY,
 case_id BIGINT NOT NULL REFERENCES support_cases(id),
 description TEXT NOT NULL,
 channel VARCHAR(10) CHECK (channel IN ('EMAIL','REMOTE')),
 minutes BIGINT NOT NULL CHECK (minutes BETWEEN 0 AND 1000000000),
 internal BOOLEAN NOT NULL DEFAULT FALSE,
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 voided BOOLEAN NOT NULL DEFAULT FALSE,
 void_reason TEXT,
 CHECK ((minutes = 0 AND channel IS NULL) OR (minutes > 0 AND channel IS NOT NULL)),
 CHECK ((voided = FALSE AND void_reason IS NULL) OR (voided = TRUE AND void_reason IS NOT NULL))
);
CREATE TABLE audit_log (
 id BIGSERIAL PRIMARY KEY,
 actor VARCHAR(80) NOT NULL,
 action VARCHAR(60) NOT NULL,
 entity_type VARCHAR(40) NOT NULL,
 entity_id BIGINT,
 details TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Locks serialize account changes, including the last-active-administrator check.
CREATE TABLE system_lock (id INTEGER PRIMARY KEY);
INSERT INTO system_lock VALUES (1);
CREATE INDEX idx_cases_institution_updated ON support_cases(institution_id, updated_at DESC);
CREATE INDEX idx_entries_case ON case_entries(case_id, id);
CREATE INDEX idx_users_institution ON app_users(institution_id);
CREATE INDEX idx_audit_created ON audit_log(created_at DESC, id DESC);
