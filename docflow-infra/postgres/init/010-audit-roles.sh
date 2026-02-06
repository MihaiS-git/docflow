#!/bin/sh
set -eu

SOC_PASSWORD_FILE="${SOC_PASSWORD_FILE:-/run/secrets/soc_password}"
if [ ! -f "$SOC_PASSWORD_FILE" ]; then
  echo "ERROR: SOC password secret file not found at: $SOC_PASSWORD_FILE" >&2
  exit 1
fi

SOC_PASSWORD="$(cat "$SOC_PASSWORD_FILE" | tr -d '\n')" # strip trailing newline (common in secrets files)

# Use psql variables to safely quote the password (avoids SQL injection / quoting bugs)
psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -v soc_password="$SOC_PASSWORD" \
  -v app_role="$POSTGRES_USER" <<'EOSQL'

-- Create audit_ro if missing (no PL/pgSQL; fully psql-driven + idempotent)
SELECT 'CREATE ROLE audit_ro'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_ro')\gexec

SELECT format(
  'CREATE ROLE soc_ro LOGIN INHERIT PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE',
  :'soc_password'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = 'soc_ro'
)\gexec

GRANT audit_ro TO soc_ro;

-- DB visibility needed for audit users
SELECT format(
  'GRANT CONNECT ON DATABASE %I TO audit_ro',
  current_database()
)\gexec
GRANT USAGE ON SCHEMA public TO audit_ro;

-- Migration-safe defaults: any future tables/sequences created by the app role grant SELECT to audit_ro
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT ON TABLES TO audit_ro',
  :'app_role'
)\gexec

SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT ON SEQUENCES TO audit_ro',
  :'app_role'
)\gexec

EOSQL