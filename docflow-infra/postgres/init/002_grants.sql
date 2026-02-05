-- ============================================================
-- 002_grants.sql
-- Database / schema grants
-- ============================================================

GRANT CONNECT ON DATABASE docflow TO audit_ro;
GRANT USAGE ON SCHEMA public TO audit_ro;
