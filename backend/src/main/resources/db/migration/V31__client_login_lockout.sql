-- Transitional local USER credential policy, independent from future policy/license login.
ALTER TABLE app_users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0
    CHECK (failed_login_attempts BETWEEN 0 AND 1000);
ALTER TABLE app_users ADD COLUMN login_locked_until TIMESTAMPTZ;
CREATE TABLE client_login_settings (
    id SMALLINT PRIMARY KEY CHECK (id=1),
    max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts BETWEEN 3 AND 10),
    lock_minutes INTEGER NOT NULL DEFAULT 15 CHECK (lock_minutes BETWEEN 1 AND 1440),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision>=1),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO client_login_settings(id) VALUES(1);
