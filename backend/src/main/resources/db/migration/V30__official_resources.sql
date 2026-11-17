-- Official SIABUC resources: link catalog only, no file upload or mirroring.
-- Existing installations start with no published resources until admins verify URLs.
CREATE TABLE official_resources (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(180) NOT NULL CHECK (LENGTH(BTRIM(title))>0),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('INSTALLER','PATCH','REPORT','DOCUMENT','VIDEO')),
    siabuc_version VARCHAR(120) NOT NULL DEFAULT '',
    description VARCHAR(1200) NOT NULL CHECK (LENGTH(BTRIM(description))>0),
    target_url VARCHAR(2000) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    revision INTEGER NOT NULL DEFAULT 1 CHECK (revision>=1),
    created_by BIGINT NOT NULL REFERENCES app_users(id),
    updated_by BIGINT NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_official_resources_status_kind ON official_resources(status, kind, updated_at DESC);
