#!/bin/sh
set -eu

# Required configuration

DB_CONTAINER="${DB_CONTAINER:-docflow-db}"
DB_USER="${DB_USER:?DB_USER is required}"
DB_NAME="${DB_NAME:?DB_NAME is required}"

OUT="auth_export_$(date +%F_%H-%M-%S).csv"

echo "=== Audit Export — Authentication Events ==="
echo "Container: $DB_CONTAINER"
echo "Database:  $DB_NAME"
echo "User:      $DB_USER"
echo

docker exec -i "$DB_CONTAINER" 
psql -U "$DB_USER" -d "$DB_NAME" 
-c "\COPY (SELECT * FROM authentication_events) TO STDOUT CSV HEADER" \

> "$OUT"

echo
echo "Export complete → $OUT"

# Usage:

# export DB_USER=...

# export DB_NAME=...

# ./export.sh
