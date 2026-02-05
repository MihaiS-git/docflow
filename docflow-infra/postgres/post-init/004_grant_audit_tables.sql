-- ============================================================
-- 004_grant_audit_tables.sql
-- Run ONCE after app created tables
-- ============================================================

GRANT SELECT ON
    admin_audit_events,
    authentication_events,
    lifecycle_denied_audit_events,
    onboarding_audit_events,
    user_identity_projection
TO audit_ro;
