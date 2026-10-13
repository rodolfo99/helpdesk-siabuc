-- Calendars are snapshotted per case so global changes cannot rewrite historical commitments.
CREATE TABLE support_settings (
 id SMALLINT PRIMARY KEY CHECK (id=1),
 configuration TEXT NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO support_settings(id,configuration) VALUES (1,
 '{"timezone":"America/Mexico_City","workDays":[1,2,3,4,5],"startTime":"09:00","endTime":"17:00","holidays":[],"goals":{"LOW":{"firstResponseMinutes":480,"resolutionMinutes":2400},"NORMAL":{"firstResponseMinutes":240,"resolutionMinutes":1440},"HIGH":{"firstResponseMinutes":120,"resolutionMinutes":480},"URGENT":{"firstResponseMinutes":30,"resolutionMinutes":240}}}');
CREATE TABLE case_profiles (
 case_id BIGINT PRIMARY KEY REFERENCES support_cases(id),
 category VARCHAR(20) NOT NULL DEFAULT 'OTHER' CHECK (category IN ('INSTALLATION','CATALOGING','LOANS','REPORTS','IMPORT','OTHER')),
 priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
 siabuc_version VARCHAR(120) NOT NULL DEFAULT '',
 operating_system VARCHAR(180) NOT NULL DEFAULT '',
 error_message TEXT NOT NULL DEFAULT '' CHECK (length(error_message)<=10000),
 reproduction_steps TEXT NOT NULL DEFAULT '' CHECK (length(reproduction_steps)<=10000),
 calendar_snapshot TEXT NOT NULL,
 opened_at TIMESTAMPTZ NOT NULL,
 resolution_started_at TIMESTAMPTZ NOT NULL,
 first_responded_at TIMESTAMPTZ,
 resolved_at TIMESTAMPTZ,
 legacy BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Only actual public CREATE_ENTRY events qualify as a response. Claim/edit/status notes do not.
INSERT INTO case_profiles(case_id,calendar_snapshot,opened_at,resolution_started_at,first_responded_at,resolved_at,legacy)
SELECT c.id,s.configuration,c.created_at,
 COALESCE((SELECT MAX(a.created_at) FROM audit_log a WHERE a.entity_type='CASE' AND a.entity_id=c.id
   AND a.action='CHANGE_CASE_STATUS' AND a.details LIKE 'Caso reabierto:%'),c.created_at),
 (SELECT MIN(e.created_at) FROM case_entries e JOIN app_users u ON u.id=e.created_by
  WHERE e.case_id=c.id AND NOT e.internal AND u.role='ADMIN' AND EXISTS
   (SELECT 1 FROM audit_log a WHERE a.entity_type='ENTRY' AND a.entity_id=e.id AND a.action='CREATE_ENTRY')),
 CASE WHEN c.status='CLOSED' THEN (SELECT MAX(a.created_at) FROM audit_log a WHERE a.entity_type='CASE'
  AND a.entity_id=c.id AND a.action='CHANGE_CASE_STATUS' AND a.details LIKE 'Caso cerrado:%') ELSE NULL END,
 TRUE
FROM support_cases c CROSS JOIN support_settings s;
CREATE INDEX idx_case_profiles_category_priority ON case_profiles(category,priority);
