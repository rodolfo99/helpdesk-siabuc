CREATE TABLE support_quotes (
 id BIGSERIAL PRIMARY KEY,
 institution_id BIGINT NOT NULL REFERENCES institutions(id),
 folio VARCHAR(100) NOT NULL CHECK(LENGTH(BTRIM(folio))>0),
 description TEXT NOT NULL CHECK(LENGTH(BTRIM(description)) BETWEEN 1 AND 10000),
 expires_on DATE NOT NULL,
 valid_from DATE NOT NULL,
 valid_until DATE NOT NULL CHECK(valid_until>=valid_from),
 agreed_amount NUMERIC(11,2) NOT NULL CHECK(agreed_amount>0 AND agreed_amount<=999999999.99),
 currency VARCHAR(3) NOT NULL DEFAULT 'MXN' CHECK(currency='MXN'),
 email_minutes BIGINT NOT NULL CHECK(email_minutes BETWEEN 0 AND 1000000000),
 remote_minutes BIGINT NOT NULL CHECK(remote_minutes BETWEEN 0 AND 1000000000),
 status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','SENT','ACCEPTED','REJECTED','CONVERTED')),
 previous_policy_id BIGINT,
 converted_policy_id BIGINT UNIQUE,conversion_folio VARCHAR(100),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 sent_at TIMESTAMPTZ,sent_evidence TEXT NOT NULL DEFAULT '',
 decision_contact VARCHAR(180),decision_evidence TEXT,decided_on DATE,
 decision_recorded_by BIGINT REFERENCES app_users(id),decision_recorded_at TIMESTAMPTZ,
 UNIQUE(id,institution_id),
 FOREIGN KEY(previous_policy_id,institution_id) REFERENCES support_policies(id,institution_id),
 FOREIGN KEY(converted_policy_id,institution_id) REFERENCES support_policies(id,institution_id),
 CHECK(email_minutes>0 OR remote_minutes>0),
 CHECK((status='DRAFT' AND sent_at IS NULL AND sent_evidence='') OR (status<>'DRAFT' AND sent_at IS NOT NULL AND LENGTH(BTRIM(sent_evidence))>0)),
 CHECK((status IN ('DRAFT','SENT') AND decision_contact IS NULL AND decision_evidence IS NULL AND decided_on IS NULL AND decision_recorded_by IS NULL AND decision_recorded_at IS NULL)
    OR (status IN ('ACCEPTED','REJECTED','CONVERTED') AND decision_contact IS NOT NULL AND LENGTH(BTRIM(decision_contact))>0
      AND decision_evidence IS NOT NULL AND LENGTH(BTRIM(decision_evidence))>0 AND decided_on IS NOT NULL AND decision_recorded_by IS NOT NULL AND decision_recorded_at IS NOT NULL)),
 CHECK((status='CONVERTED')=(converted_policy_id IS NOT NULL)),
 CHECK((status='CONVERTED')=(conversion_folio IS NOT NULL))
);
CREATE UNIQUE INDEX ux_quote_folio ON support_quotes(LOWER(BTRIM(folio)));
CREATE INDEX idx_quotes_institution ON support_quotes(institution_id,id DESC);
CREATE TABLE quote_history (
 id BIGSERIAL PRIMARY KEY,quote_id BIGINT NOT NULL REFERENCES support_quotes(id),
 action VARCHAR(30) NOT NULL CHECK(action IN ('CREATE_QUOTE','UPDATE_QUOTE','SEND_QUOTE','ACCEPT_QUOTE','REJECT_QUOTE','CONVERT_QUOTE')),
 description TEXT NOT NULL,created_by BIGINT NOT NULL REFERENCES app_users(id),created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_quote_history ON quote_history(quote_id,id DESC);
CREATE TABLE policy_renewals (
 id BIGSERIAL PRIMARY KEY,institution_id BIGINT NOT NULL REFERENCES institutions(id),
 previous_policy_id BIGINT NOT NULL UNIQUE,new_policy_id BIGINT NOT NULL UNIQUE,quote_id BIGINT NOT NULL UNIQUE,
 created_by BIGINT NOT NULL REFERENCES app_users(id),created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 FOREIGN KEY(previous_policy_id,institution_id) REFERENCES support_policies(id,institution_id),
 FOREIGN KEY(new_policy_id,institution_id) REFERENCES support_policies(id,institution_id),
 FOREIGN KEY(quote_id,institution_id) REFERENCES support_quotes(id,institution_id),
 CHECK(previous_policy_id<>new_policy_id)
);
CREATE TABLE renewal_settings (
 id SMALLINT PRIMARY KEY CHECK(id=1),enabled BOOLEAN NOT NULL DEFAULT TRUE,
 days_before_expiry INTEGER NOT NULL DEFAULT 30 CHECK(days_before_expiry BETWEEN 0 AND 365),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO renewal_settings(id) VALUES(1);
CREATE TABLE renewal_notices (
 id BIGSERIAL PRIMARY KEY,policy_id BIGINT NOT NULL,institution_id BIGINT NOT NULL REFERENCES institutions(id),
 institution_name VARCHAR(180) NOT NULL,policy_folio VARCHAR(100) NOT NULL,expiration_date DATE NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),UNIQUE(policy_id,expiration_date),
 FOREIGN KEY(policy_id,institution_id) REFERENCES support_policies(id,institution_id)
);
CREATE TABLE renewal_notice_reads (
 notice_id BIGINT NOT NULL REFERENCES renewal_notices(id),user_id BIGINT NOT NULL REFERENCES app_users(id),
 read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(notice_id,user_id)
);
