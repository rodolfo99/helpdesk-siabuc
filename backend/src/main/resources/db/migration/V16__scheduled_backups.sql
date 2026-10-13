CREATE TABLE backup_settings (
 id SMALLINT PRIMARY KEY CHECK (id=1),
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 daily_time TIME NOT NULL DEFAULT '03:00',
 time_zone VARCHAR(80) NOT NULL DEFAULT 'America/Mexico_City',
 retention_days INTEGER NOT NULL DEFAULT 30 CHECK (retention_days BETWEEN 1 AND 3650),
 keep_last INTEGER NOT NULL DEFAULT 7 CHECK (keep_last BETWEEN 1 AND 1000),
 destination_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_by BIGINT REFERENCES app_users(id),
 CHECK (NOT enabled OR destination_confirmed)
);
INSERT INTO backup_settings(id) VALUES (1);
CREATE TABLE backup_runtime (
 id SMALLINT PRIMARY KEY CHECK (id=1),
 last_seen_at TIMESTAMPTZ,
 destination_label VARCHAR(1000) NOT NULL DEFAULT '',
 writable BOOLEAN NOT NULL DEFAULT FALSE,
 last_error VARCHAR(2000),
 lock_owner UUID,
 lock_until TIMESTAMPTZ
);
INSERT INTO backup_runtime(id) VALUES (1);
CREATE TABLE backup_runs (
 id BIGSERIAL PRIMARY KEY,
 kind VARCHAR(10) NOT NULL CHECK (kind IN ('SCHEDULED','MANUAL')),
 scheduled_key DATE UNIQUE,
 status VARCHAR(15) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','RETRYING','SUCCEEDED','FAILED','CANCELLED')),
 requested_by BIGINT REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 started_at TIMESTAMPTZ,
 finished_at TIMESTAMPTZ,
 retry_at TIMESTAMPTZ,
 attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
 owner UUID,
 filename VARCHAR(180),
 sha256 CHAR(64),
 size_bytes BIGINT,
 last_error VARCHAR(2000),
 purged_at TIMESTAMPTZ,
 CHECK ((kind='SCHEDULED' AND scheduled_key IS NOT NULL) OR (kind='MANUAL' AND scheduled_key IS NULL))
);
CREATE INDEX idx_backup_runs_status ON backup_runs(status,retry_at,created_at);
CREATE TABLE backup_attempts (
 run_id BIGINT NOT NULL REFERENCES backup_runs(id),
 attempt INTEGER NOT NULL CHECK (attempt BETWEEN 1 AND 3),
 started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 finished_at TIMESTAMPTZ,
 status VARCHAR(15) NOT NULL CHECK (status IN ('RUNNING','SUCCEEDED','FAILED')),
 error VARCHAR(2000),
 PRIMARY KEY(run_id,attempt)
);
