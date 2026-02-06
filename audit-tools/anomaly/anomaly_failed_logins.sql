SELECT
  username,
  ip,
  COUNT(*) AS failures
FROM authentication_events
WHERE result = 'FAILURE'
GROUP BY username, ip
HAVING COUNT(*) >= 5
ORDER BY failures DESC;
