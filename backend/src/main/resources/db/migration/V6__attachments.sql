-- File bytes live in PostgreSQL so pg_dump/restore includes the original attachments.
CREATE TABLE case_attachments (
 id BIGSERIAL PRIMARY KEY,
 case_id BIGINT NOT NULL REFERENCES support_cases(id),
 entry_id BIGINT REFERENCES case_entries(id),
 uploaded_by BIGINT NOT NULL REFERENCES app_users(id),
 request_id UUID NOT NULL,
 filename VARCHAR(180) NOT NULL,
 content_type VARCHAR(100) NOT NULL,
 size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
 sha256 CHAR(64) NOT NULL,
 file_bytes BYTEA NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(uploaded_by,request_id),
 CHECK (octet_length(file_bytes)=size_bytes)
);
CREATE INDEX idx_attachments_case_entry ON case_attachments(case_id,entry_id,id);
