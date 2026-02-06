-- replace :username with the target email/username
SELECT
  timestamp,
  result,
  ip,
  user_agent,
  source,
  request_id
FROM authentication_events
WHERE username = :'username'
ORDER BY timestamp DESC;
-- Run: psql -v username='user@example.com' -f incident_user_auth.sql
