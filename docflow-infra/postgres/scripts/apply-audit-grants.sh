#!/bin/sh
set -eu

echo "Audit grant job starting..."

DB_HOST="${DB_HOST:-docflow-db}"
DB_USER="${POSTGRES_USER:?POSTGRES_USER is required}"
DB_NAME="${POSTGRES_DB:?POSTGRES_DB is required}"

MAX_WAIT="${MAX_WAIT:-60}"
SLEEP_SEC="${SLEEP_SEC:-2}"
elapsed=0

echo "Waiting for audit tables to appear..."

while :; do
  exists="$(psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -tAc "SELECT to_regclass('public.onboarding_audit_events')")"
  exists="$(echo "$exists" | tr -d '[:space:]')"

  if [ -n "$exists" ]; then
    break
  fi

  if [ "$elapsed" -ge "$MAX_WAIT" ]; then
    echo "Timeout waiting for audit tables (waited ${MAX_WAIT}s)" >&2
    exit 1
  fi

  sleep "$SLEEP_SEC"
  elapsed=$((elapsed + SLEEP_SEC))
done

echo "Applying audit grants..."

psql -v ON_ERROR_STOP=1 -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" <<'EOSQL'
DO $$
BEGIN
  IF to_regclass('public.admin_audit_events') IS NOT NULL THEN
    GRANT SELECT ON public.admin_audit_events TO audit_ro;
  END IF;

  IF to_regclass('public.authentication_events') IS NOT NULL THEN
    GRANT SELECT ON public.authentication_events TO audit_ro;
  END IF;

  IF to_regclass('public.lifecycle_denied_audit_events') IS NOT NULL THEN
    GRANT SELECT ON public.lifecycle_denied_audit_events TO audit_ro;
  END IF;

  IF to_regclass('public.onboarding_audit_events') IS NOT NULL THEN
    GRANT SELECT ON public.onboarding_audit_events TO audit_ro;
  END IF;

  IF to_regclass('public.user_identity_projection') IS NOT NULL THEN
    GRANT SELECT ON public.user_identity_projection TO audit_ro;
  END IF;
END
$$;
EOSQL

echo "Audit grants successfully applied."