SELECT timestamp, 'AUTH' AS source, username AS actor, result AS detail
FROM authentication_events

UNION ALL

SELECT timestamp, 'ADMIN', actor_user_id::text, action_type
FROM admin_audit_events

UNION ALL

SELECT timestamp, 'DENIED', subject_id, reason_code
FROM lifecycle_denied_audit_events

UNION ALL

SELECT timestamp, 'ONBOARD', actor_user_id::text, outcome
FROM onboarding_audit_events

ORDER BY timestamp DESC;
