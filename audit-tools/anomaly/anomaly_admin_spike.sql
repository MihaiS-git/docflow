SELECT
  actor_user_id,
  COUNT(*) AS admin_actions
FROM admin_audit_events
WHERE timestamp > now() - interval '1 hour'
GROUP BY actor_user_id
ORDER BY admin_actions DESC;
