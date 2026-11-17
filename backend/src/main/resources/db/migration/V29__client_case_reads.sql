-- Per-customer, per-ticket last visible entry marker. Nothing is marked read by listing tickets.
CREATE TABLE client_case_reads (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    case_id BIGINT NOT NULL REFERENCES support_cases(id) ON DELETE CASCADE,
    last_seen_entry_id BIGINT NOT NULL DEFAULT 0 CHECK (last_seen_entry_id >= 0),
    read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, case_id)
);
CREATE INDEX idx_client_case_reads_case ON client_case_reads(case_id);
