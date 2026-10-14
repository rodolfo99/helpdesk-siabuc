-- Internal coordination is separate from deliberately published customer-facing text.
CREATE TABLE support_incidents (
 id BIGSERIAL PRIMARY KEY,
 title VARCHAR(220) NOT NULL CHECK (LENGTH(BTRIM(title)) > 0),
 description TEXT NOT NULL CHECK (LENGTH(BTRIM(description)) BETWEEN 1 AND 20000),
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED')),
 revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
 public_title VARCHAR(220) NOT NULL DEFAULT '',
 public_summary TEXT NOT NULL DEFAULT '' CHECK (LENGTH(public_summary) <= 10000),
 public_status VARCHAR(20) CHECK (public_status IN ('OPEN','IN_PROGRESS','RESOLVED')),
 published BOOLEAN NOT NULL DEFAULT FALSE,
 published_at TIMESTAMPTZ,
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 updated_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CHECK (NOT published OR (LENGTH(BTRIM(public_title)) > 0 AND LENGTH(BTRIM(public_summary)) > 0 AND public_status IS NOT NULL AND published_at IS NOT NULL))
);
CREATE INDEX idx_incidents_status_updated ON support_incidents(status,updated_at DESC,id DESC);
CREATE TABLE incident_case_links (
 incident_id BIGINT NOT NULL REFERENCES support_incidents(id),
 case_id BIGINT NOT NULL REFERENCES support_cases(id),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 linked_by BIGINT NOT NULL REFERENCES app_users(id),
 linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 removed_by BIGINT REFERENCES app_users(id),
 removed_at TIMESTAMPTZ,
 PRIMARY KEY(incident_id,case_id)
);
CREATE INDEX idx_incident_links_case ON incident_case_links(case_id,active);
CREATE TABLE incident_history (
 id BIGSERIAL PRIMARY KEY,
 incident_id BIGINT NOT NULL REFERENCES support_incidents(id),
 action VARCHAR(20) NOT NULL CHECK (action IN ('CREATE','EDIT','PROGRESS','PUBLISH','UNPUBLISH','LINK','UNLINK')),
 revision INTEGER NOT NULL,
 title VARCHAR(220) NOT NULL,
 description TEXT NOT NULL,
 status VARCHAR(20) NOT NULL,
 public_title VARCHAR(220) NOT NULL,
 public_summary TEXT NOT NULL,
 public_status VARCHAR(20),
 published BOOLEAN NOT NULL,
 note VARCHAR(5000) NOT NULL DEFAULT '',
 case_id BIGINT REFERENCES support_cases(id),
 actor BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_incident_history_incident ON incident_history(incident_id,id DESC);
