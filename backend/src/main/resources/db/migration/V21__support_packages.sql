CREATE TABLE support_packages (
 id BIGSERIAL PRIMARY KEY, code VARCHAR(60) NOT NULL UNIQUE,
 active BOOLEAN NOT NULL DEFAULT TRUE, current_revision INTEGER NOT NULL DEFAULT 1,
 created_by BIGINT NOT NULL REFERENCES app_users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE support_package_versions (
 id BIGSERIAL PRIMARY KEY, package_id BIGINT NOT NULL REFERENCES support_packages(id), revision INTEGER NOT NULL,
 name VARCHAR(180) NOT NULL, description TEXT NOT NULL, amount NUMERIC(11,2) NOT NULL CHECK(amount>0),
 currency VARCHAR(3) NOT NULL DEFAULT 'MXN' CHECK(currency='MXN'),
 email_minutes BIGINT NOT NULL CHECK(email_minutes BETWEEN 0 AND 1000000000), remote_minutes BIGINT NOT NULL CHECK(remote_minutes BETWEEN 0 AND 1000000000),
 created_by BIGINT NOT NULL REFERENCES app_users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(package_id,revision), CHECK(email_minutes>0 OR remote_minutes>0)
);
ALTER TABLE support_quotes ADD COLUMN package_version_id BIGINT REFERENCES support_package_versions(id);
ALTER TABLE support_quotes ADD COLUMN package_override_reason TEXT;
