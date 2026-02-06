SELECT
  timestamp,
  reason_code,
  http_method,
  path,
  subject_id,
  ip,
  user_agent,
  request_id
FROM lifecycle_denied_audit_events
ORDER BY timestamp DESC;
