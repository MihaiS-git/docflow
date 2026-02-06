SELECT
  timestamp,
  outcome,
  invite_id,
  actor_user_id,
  subject_id,
  ip,
  user_agent,
  request_id
FROM onboarding_audit_events
ORDER BY timestamp DESC;
