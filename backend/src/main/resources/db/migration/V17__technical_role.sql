-- Technical staff have global case access and no institution-scoped client identity.
ALTER TABLE app_users DROP CONSTRAINT app_users_role_check;
ALTER TABLE app_users DROP CONSTRAINT app_users_scope_check;
ALTER TABLE app_users ADD CONSTRAINT app_users_role_check CHECK (role IN ('ADMIN','USER','BILLING','TECH'));
ALTER TABLE app_users ADD CONSTRAINT app_users_scope_check CHECK (
 (role IN ('ADMIN','BILLING','TECH') AND institution_id IS NULL) OR (role='USER' AND institution_id IS NOT NULL)
);
