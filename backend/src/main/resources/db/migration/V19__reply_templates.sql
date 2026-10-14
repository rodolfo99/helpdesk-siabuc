CREATE TABLE reply_templates (
 id BIGSERIAL PRIMARY KEY,
 title VARCHAR(180) NOT NULL CHECK (length(trim(title))>0),
 category VARCHAR(30) NOT NULL CHECK (category IN ('INSTALLATION','CATALOGING','LOANS','REPORTS','IMPORT','OTHER')),
 siabuc_version VARCHAR(120) NOT NULL DEFAULT '',
 body TEXT NOT NULL CHECK (length(trim(body)) BETWEEN 1 AND 10000),
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
 revision INTEGER NOT NULL DEFAULT 1 CHECK (revision>0),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX reply_templates_status_category_idx ON reply_templates(status,category,id);
CREATE TABLE reply_template_history (
 id BIGSERIAL PRIMARY KEY,
 template_id BIGINT NOT NULL REFERENCES reply_templates(id),
 revision INTEGER NOT NULL,
 actor VARCHAR(100) NOT NULL,
 action VARCHAR(30) NOT NULL,
 snapshot JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(template_id,revision)
);
