#!/bin/sh
set -eu

SOC_PASSWORD_FILE="${SOC_PASSWORD_FILE:-/run/secrets/soc_password}"

if [ -f "$SOC_PASSWORD_FILE" ]; then
  SOC_PASSWORD="$(tr -d '\n' < "$SOC_PASSWORD_FILE")"
else
  SOC_PASSWORD=""
fi

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -v soc_password="$SOC_PASSWORD" \
  -v app_role="$POSTGRES_USER" <<'EOSQL'

-- -----------------------------------------------------
-- Create audit_ro (no login)
-- -----------------------------------------------------
SELECT 'CREATE ROLE audit_ro NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_ro')\gexec

-- -----------------------------------------------------
-- Optional SOC login role
-- -----------------------------------------------------
SELECT format(
  'CREATE ROLE soc_ro LOGIN INHERIT PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE',
  :'soc_password'
)
WHERE :'soc_password' <> ''
  AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'soc_ro')\gexec

-- If soc_ro exists, inherit audit_ro
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'soc_ro') THEN
    GRANT audit_ro TO soc_ro;
  END IF;
END
$$;

-- -----------------------------------------------------
-- Database visibility
-- -----------------------------------------------------
SELECT format(
  'GRANT CONNECT ON DATABASE %I TO audit_ro',
  current_database()
)\gexec

GRANT USAGE ON SCHEMA public TO audit_ro;

-- -----------------------------------------------------
-- Future-proof default privileges
-- -----------------------------------------------------
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT ON TABLES TO audit_ro',
  :'app_role'
)\gexec

SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT ON SEQUENCES TO audit_ro',
  :'app_role'
)\gexec

EOSQL