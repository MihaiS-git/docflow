-- replace :subject with the target subject
SELECT
  timestamp,
  reason_code,
  path,
  ip,
  request_id
FROM lifecycle_denied_audit_events
WHERE subject_id = :'subject'
ORDER BY timestamp DESC;
-- Run: psql -v username='user@example.com' -f incident_user_denied.sql