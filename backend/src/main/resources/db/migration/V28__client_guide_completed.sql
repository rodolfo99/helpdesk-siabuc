-- Client onboarding is currently associated with the authenticated Helpdesk user.
-- A future policy+email login will need policy-specific onboarding semantics.
CREATE TABLE client_guide_completed (
  user_id BIGINT PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
  completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
