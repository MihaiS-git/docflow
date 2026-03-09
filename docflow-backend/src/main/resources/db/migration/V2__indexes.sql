-- =====================================================
-- Audit signing keys: PostgreSQL hard invariants
-- Compatible with ddl-auto=validate
-- =====================================================

-- -----------------------------------------------------
-- Ensure table exists (defensive safety)
-- -----------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'audit_signing_keys'
    ) THEN
        RAISE EXCEPTION 'audit_signing_keys table does not exist';
    END IF;
END
$$;

-- -----------------------------------------------------
-- Critical invariant:
-- At most ONE active signing key
-- (Hibernate cannot generate partial indexes)
-- -----------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS ux_audit_signing_keys_active_true
    ON public.audit_signing_keys (active)
    WHERE active = true;