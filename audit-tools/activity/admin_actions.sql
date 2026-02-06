SELECT
  timestamp,
  actor_user_id,
  action_type,
  subject_id,
  target_user_id,
  ip,
  request_id,
  metadata
FROM admin_audit_events
ORDER BY timestamp DESC;
