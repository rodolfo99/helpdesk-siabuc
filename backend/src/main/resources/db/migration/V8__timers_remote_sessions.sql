CREATE TABLE work_timers (
 id BIGSERIAL PRIMARY KEY,
 case_id BIGINT NOT NULL REFERENCES support_cases(id),
 admin_id BIGINT NOT NULL REFERENCES app_users(id),
 status VARCHAR(12) NOT NULL CHECK (status IN ('RUNNING','PAUSED','FINISHED','COMMITTED','CANCELLED')),
 elapsed_millis BIGINT NOT NULL DEFAULT 0 CHECK (elapsed_millis >= 0),
 segment_started_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 entry_id BIGINT UNIQUE REFERENCES case_entries(id),
 confirmed_minutes BIGINT,
 adjustment_reason TEXT NOT NULL DEFAULT '',
 CHECK ((status = 'RUNNING') = (segment_started_at IS NOT NULL)),
 CHECK ((status = 'COMMITTED') = (entry_id IS NOT NULL)),
 CHECK (confirmed_minutes IS NULL OR confirmed_minutes BETWEEN 1 AND 1000000000)
);
CREATE UNIQUE INDEX idx_timer_one_active_per_admin ON work_timers(admin_id) WHERE status IN ('RUNNING','PAUSED','FINISHED');
CREATE INDEX idx_timer_case_admin ON work_timers(case_id,admin_id,id DESC);

CREATE TABLE remote_sessions (
 id BIGSERIAL PRIMARY KEY,
 case_id BIGINT NOT NULL REFERENCES support_cases(id),
 admin_id BIGINT NOT NULL REFERENCES app_users(id),
 starts_at TIMESTAMPTZ NOT NULL,
 ends_at TIMESTAMPTZ NOT NULL,
 note TEXT NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED','CONFIRMED','DECLINED','CANCELLED','COMPLETED')),
 response TEXT NOT NULL DEFAULT '',
 responded_by BIGINT REFERENCES app_users(id),
 replaces_id BIGINT UNIQUE REFERENCES remote_sessions(id),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CHECK (ends_at > starts_at AND ends_at <= starts_at + INTERVAL '12 hours')
);
CREATE INDEX idx_remote_sessions_case ON remote_sessions(case_id,starts_at DESC);
CREATE INDEX idx_remote_sessions_admin_times ON remote_sessions(admin_id,starts_at,ends_at) WHERE status IN ('PROPOSED','CONFIRMED');
