#!/bin/sh
set -eu

echo "Audit grant job starting..."

DB_HOST="${DB_HOST:-docflow-db}"
DB_USER="${POSTGRES_USER:?POSTGRES_USER is required}"
DB_NAME="${POSTGRES_DB:?POSTGRES_DB is required}"

psql -v ON_ERROR_STOP=1 -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" <<'EOSQL'

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT tablename
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename IN (
        'admin_audit_events',
        'authentication_events',
        'credential_lifecycle_audit_events',
        'identity_projection_audit_events',
        'lifecycle_denied_audit_events',
        'rbac_denied_audit_events',
        'onboarding_audit_events',
        'sensitive_access_audit_events',
        'unauthenticated_access_audit_events',
        'audit_chain_state',
        'audit_export_snapshot',
        'audit_retention_policies',
        'audit_signing_keys'
      )
  LOOP
    EXECUTE format(
      'GRANT SELECT ON TABLE public.%I TO audit_ro',
      r.tablename
    );
  END LOOP;
END
$$;

EOSQL

echo "Audit grants successfully applied."