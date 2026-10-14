CREATE TABLE saved_case_views (
 id BIGSERIAL PRIMARY KEY,
 owner_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
 name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
 filters JSONB NOT NULL CHECK (jsonb_typeof(filters) = 'object'),
 revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX saved_case_views_owner_name_idx ON saved_case_views(owner_id,lower(name));
CREATE TABLE sla_alert_settings (
 id INTEGER PRIMARY KEY CHECK (id=1),
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 lead_minutes INTEGER NOT NULL DEFAULT 60 CHECK (lead_minutes BETWEEN 1 AND 43200),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO sla_alert_settings(id) VALUES (1);
CREATE TABLE sla_alert_deliveries (
 case_id BIGINT NOT NULL REFERENCES support_cases(id),
 goal VARCHAR(30) NOT NULL CHECK (goal IN ('FIRST_RESPONSE','RESOLUTION')),
 due_at TIMESTAMPTZ NOT NULL,
 recipient_id BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY (case_id,goal,due_at,recipient_id)
);
