SELECT
  timestamp,
  username,
  result,
  ip,
  user_agent,
  source,
  request_id
FROM authentication_events
ORDER BY timestamp DESC;
