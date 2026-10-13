-- Administrative receipts are issued once on first authorized download.
-- Financial movements remain in payments; an issue never credits support or records income.
CREATE TABLE payment_receipts (
 id BIGSERIAL PRIMARY KEY,
 payment_id BIGINT NOT NULL UNIQUE REFERENCES payments(id),
 folio VARCHAR(50) NOT NULL UNIQUE,
 snapshot_version INTEGER NOT NULL DEFAULT 1 CHECK (snapshot_version=1),
 snapshot JSONB NOT NULL CHECK (jsonb_typeof(snapshot)='object'),
 issued_by BIGINT NOT NULL REFERENCES app_users(id),
 issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CHECK (LENGTH(BTRIM(folio))>0)
);
CREATE INDEX idx_payment_receipts_issued ON payment_receipts(issued_at,id);
