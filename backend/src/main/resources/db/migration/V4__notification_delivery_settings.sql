-- Singleton shared by all application instances. Pause survives restarts and backups.
CREATE TABLE notification_settings (
 id SMALLINT PRIMARY KEY CHECK (id=1),
 paused BOOLEAN NOT NULL DEFAULT FALSE,
 suppressed_through_id BIGINT NOT NULL DEFAULT 0 CHECK (suppressed_through_id>=0)
);
INSERT INTO notification_settings(id,paused) VALUES (1,FALSE);

-- Suppression preserves the historical notice, without allowing subsequent delivery.
ALTER TABLE notification_outbox DROP CONSTRAINT notification_outbox_status_check;
ALTER TABLE notification_outbox ADD CONSTRAINT notification_outbox_status_check
 CHECK (status IN ('PENDING','SENDING','SENT','FAILED','SKIPPED','SUPPRESSED'));
