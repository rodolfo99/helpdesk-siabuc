CREATE TABLE knowledge_articles (
 id BIGSERIAL PRIMARY KEY,
 title VARCHAR(180) NOT NULL CHECK (LENGTH(BTRIM(title)) > 0),
 category VARCHAR(30) NOT NULL CHECK (category IN ('INSTALLATION','CATALOGING','LOANS','REPORTS','IMPORT','OTHER')),
 siabuc_version VARCHAR(120) NOT NULL DEFAULT '',
 summary VARCHAR(1000) NOT NULL CHECK (LENGTH(BTRIM(summary)) > 0),
 body TEXT NOT NULL CHECK (LENGTH(BTRIM(body)) > 0 AND LENGTH(body) <= 50000),
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
 revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 updated_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 published_at TIMESTAMPTZ
);
CREATE INDEX idx_knowledge_status_category ON knowledge_articles(status,category,updated_at DESC,id DESC);

-- Immutable editorial snapshots. Only administrators can read these revisions.
CREATE TABLE knowledge_article_history (
 id BIGSERIAL PRIMARY KEY,
 article_id BIGINT NOT NULL REFERENCES knowledge_articles(id),
 revision INTEGER NOT NULL CHECK (revision > 0),
 action VARCHAR(30) NOT NULL CHECK (action IN ('CREATE','EDIT','PUBLISH','UNPUBLISH','ARCHIVE','RESTORE_DRAFT')),
 title VARCHAR(180) NOT NULL,
 category VARCHAR(30) NOT NULL,
 siabuc_version VARCHAR(120) NOT NULL,
 summary VARCHAR(1000) NOT NULL,
 body TEXT NOT NULL,
 status VARCHAR(20) NOT NULL,
 change_note VARCHAR(1000) NOT NULL DEFAULT '',
 actor BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(article_id,revision)
);
CREATE INDEX idx_knowledge_history_article ON knowledge_article_history(article_id,revision DESC);

-- Retired links and their events are retained; customer reads join the CURRENT published article.
CREATE TABLE knowledge_case_links (
 case_id BIGINT NOT NULL REFERENCES support_cases(id),
 article_id BIGINT NOT NULL REFERENCES knowledge_articles(id),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 linked_by BIGINT NOT NULL REFERENCES app_users(id),
 linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 removed_by BIGINT REFERENCES app_users(id),
 removed_at TIMESTAMPTZ,
 PRIMARY KEY(case_id,article_id)
);
CREATE INDEX idx_knowledge_links_article ON knowledge_case_links(article_id,active);
CREATE TABLE knowledge_link_events (
 id BIGSERIAL PRIMARY KEY,
 case_id BIGINT NOT NULL REFERENCES support_cases(id),
 article_id BIGINT NOT NULL REFERENCES knowledge_articles(id),
 action VARCHAR(20) NOT NULL CHECK (action IN ('LINK','UNLINK')),
 actor BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_knowledge_link_events_case ON knowledge_link_events(case_id,id DESC);
