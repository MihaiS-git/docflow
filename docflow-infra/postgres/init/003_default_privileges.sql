-- ============================================================
-- Default privileges for audit visibility
-- Applies to tables created by the app DB user
-- ============================================================

-- IMPORTANT:
-- Replace docflow_app with ${DOCFLOW_DB_USER} actual value
-- (env substitution does NOT work inside SQL)
ALTER DEFAULT PRIVILEGES
FOR ROLE postgres
IN SCHEMA public
GRANT SELECT ON TABLES TO audit_ro;

-- Sequences (needed if auditors query IDs safely)
ALTER DEFAULT PRIVILEGES
FOR ROLE postgres
IN SCHEMA public
GRANT SELECT ON SEQUENCES TO audit_ro;
