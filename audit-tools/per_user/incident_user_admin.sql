-- replace :user_id with the target user_id
SELECT
  timestamp,
  action_type,
  subject_id,
  target_user_id,
  ip,
  metadata,
  request_id
FROM admin_audit_events
WHERE actor_user_id = :'user_id'
ORDER BY timestamp DESC;
-- Run: psql -v username='user@example.com' -f incident_user_admin.sql