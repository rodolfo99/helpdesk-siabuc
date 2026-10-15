-- OPEN retains its identifier, cases, timestamps, entries and balances. It is displayed as Recibido.
ALTER TABLE support_cases DROP CONSTRAINT support_cases_status_check;
ALTER TABLE support_cases ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE support_cases ADD CONSTRAINT support_cases_status_check
 CHECK (status IN ('OPEN','IN_PROGRESS','WAITING_CLIENT','RESOLVED','CLOSED'));

-- Previously saved OPEN filters meant all unfinished cases; preserve that intent.
UPDATE saved_case_views SET filters=jsonb_set(filters,'{status}','"active"'::jsonb)
 WHERE filters->>'status'='OPEN';
