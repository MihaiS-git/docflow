SELECT
  subject_id,
  COUNT(*) AS denied_attempts
FROM lifecycle_denied_audit_events
WHERE timestamp > now() - interval '1 hour'
GROUP BY subject_id
ORDER BY denied_attempts DESC;
