-- One institutional response per case; reopening does not create another survey.
CREATE TABLE satisfaction_surveys (
 id BIGSERIAL PRIMARY KEY,
 request_id UUID NOT NULL UNIQUE,
 case_id BIGINT NOT NULL UNIQUE REFERENCES support_cases(id),
 institution_id BIGINT NOT NULL REFERENCES institutions(id),
 institution_name VARCHAR(180) NOT NULL,
 case_title VARCHAR(220) NOT NULL,
 respondent_id BIGINT NOT NULL REFERENCES app_users(id),
 respondent_username VARCHAR(80) NOT NULL,
 respondent_name VARCHAR(180) NOT NULL,
 administrator_id BIGINT REFERENCES app_users(id),
 administrator_username VARCHAR(80) NOT NULL DEFAULT '',
 administrator_name VARCHAR(180) NOT NULL DEFAULT '',
 score SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
 comment TEXT NOT NULL DEFAULT '' CHECK (length(comment)<=2000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_satisfaction_date ON satisfaction_surveys(created_at DESC,id DESC);
CREATE INDEX idx_satisfaction_institution_date ON satisfaction_surveys(institution_id,created_at DESC);
CREATE INDEX idx_satisfaction_admin_date ON satisfaction_surveys(administrator_id,created_at DESC);
