-- Transaction-time history starts here. No invented pre-upgrade budget movements.
CREATE TABLE statement_history_origin (id INTEGER PRIMARY KEY CHECK(id=1), opened_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp());
INSERT INTO statement_history_origin(id) VALUES(1);
CREATE TABLE statement_versions (
 id BIGSERIAL PRIMARY KEY,
 kind VARCHAR(20) NOT NULL CHECK(kind IN ('institutions','support_policies','payments')),
 entity_id BIGINT NOT NULL,
 institution_id BIGINT NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
 opening BOOLEAN NOT NULL DEFAULT FALSE,
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 data JSONB NOT NULL
);
CREATE INDEX idx_statement_versions_cutoff ON statement_versions(institution_id,kind,entity_id,recorded_at DESC,id DESC);
CREATE FUNCTION statement_public_row(kind TEXT, r JSONB) RETURNS JSONB LANGUAGE SQL IMMUTABLE AS $$
SELECT CASE kind
 WHEN 'institutions' THEN jsonb_build_object('id',r->'id','name',r->'name','active',r->'active','emailAllocated',r->'email_allocated_minutes','emailUsed',r->'email_used_minutes','remoteAllocated',r->'remote_allocated_minutes','remoteUsed',r->'remote_used_minutes')
 WHEN 'support_policies' THEN jsonb_build_object('id',r->'id','folio',r->'folio','validFrom',r->'valid_from','validUntil',r->'valid_until','amount',r->'agreed_amount','currency',r->'currency','emailMinutes',r->'email_minutes','remoteMinutes',r->'remote_minutes','status',r->'status')
 WHEN 'payments' THEN jsonb_build_object('id',r->'id','policyId',r->'policy_id','reference',r->'reference','amount',r->'amount','currency',r->'currency','paidOn',r->'paid_on','method',r->'method','emailMinutes',r->'email_minutes','remoteMinutes',r->'remote_minutes','voided',r->'voided','createdAt',r->'created_at') END;
$$;
INSERT INTO statement_versions(kind,entity_id,institution_id,recorded_at,opening,data)
SELECT 'institutions',i.id,i.id,o.opened_at,TRUE,statement_public_row('institutions',to_jsonb(i)) FROM institutions i CROSS JOIN statement_history_origin o;
INSERT INTO statement_versions(kind,entity_id,institution_id,recorded_at,opening,data)
SELECT 'support_policies',p.id,p.institution_id,o.opened_at,TRUE,statement_public_row('support_policies',to_jsonb(p)) FROM support_policies p CROSS JOIN statement_history_origin o;
INSERT INTO statement_versions(kind,entity_id,institution_id,recorded_at,opening,data)
SELECT 'payments',p.id,p.institution_id,o.opened_at,TRUE,statement_public_row('payments',to_jsonb(p)) FROM payments p CROSS JOIN statement_history_origin o;
CREATE FUNCTION capture_statement_version() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE r JSONB; s JSONB; institution BIGINT;
BEGIN
 r := CASE WHEN TG_OP='DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
 s := statement_public_row(TG_TABLE_NAME,r);
 IF TG_OP='UPDATE' AND s=statement_public_row(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN NEW; END IF;
 institution := CASE WHEN TG_TABLE_NAME='institutions' THEN (r->>'id')::BIGINT ELSE (r->>'institution_id')::BIGINT END;
 INSERT INTO statement_versions(kind,entity_id,institution_id,deleted,data) VALUES(TG_TABLE_NAME,(r->>'id')::BIGINT,institution,TG_OP='DELETE',s);
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER statement_institution_version AFTER INSERT OR UPDATE OR DELETE ON institutions FOR EACH ROW EXECUTE FUNCTION capture_statement_version();
CREATE TRIGGER statement_policy_version AFTER INSERT OR UPDATE OR DELETE ON support_policies FOR EACH ROW EXECUTE FUNCTION capture_statement_version();
CREATE TRIGGER statement_payment_version AFTER INSERT OR UPDATE OR DELETE ON payments FOR EACH ROW EXECUTE FUNCTION capture_statement_version();
CREATE TABLE institution_statements (
 id BIGSERIAL PRIMARY KEY,
 institution_id BIGINT NOT NULL REFERENCES institutions(id),
 request_id UUID NOT NULL UNIQUE,
 cutoff DATE NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 created_by BIGINT NOT NULL REFERENCES app_users(id),
 snapshot JSONB NOT NULL
);
CREATE INDEX idx_statements_institution ON institution_statements(institution_id,id DESC);
CREATE FUNCTION prevent_statement_rewrite() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'El historial y los estados de cuenta emitidos son inmutables'; END;
$$;
CREATE TRIGGER statement_versions_immutable BEFORE UPDATE OR DELETE ON statement_versions FOR EACH ROW EXECUTE FUNCTION prevent_statement_rewrite();
CREATE TRIGGER institution_statements_immutable BEFORE UPDATE OR DELETE ON institution_statements FOR EACH ROW EXECUTE FUNCTION prevent_statement_rewrite();
CREATE TRIGGER statement_origin_immutable BEFORE UPDATE OR DELETE ON statement_history_origin FOR EACH ROW EXECUTE FUNCTION prevent_statement_rewrite();
