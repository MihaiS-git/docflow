SELECT
  id,
  email,
  display_name,
  status,
  last_login_at,
  last_login_ip,
  tenant_id
FROM users
ORDER BY created_at DESC;
