-- =====================================================
-- Audit signing keys: constraints + indexes (PostgreSQL)
-- =====================================================

-- -----------------------------------------------------
-- Fingerprint uniqueness (cryptographic identity)
-- -----------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_audit_signing_keys_fingerprint'
    ) THEN
ALTER TABLE audit_signing_keys
    ADD CONSTRAINT uk_audit_signing_keys_fingerprint
        UNIQUE (fingerprint_sha256_hex);
END IF;
END
$$;

-- -----------------------------------------------------
-- Enforce deterministic keyId format
-- -----------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_audit_signing_keys_key_id_format'
    ) THEN
ALTER TABLE audit_signing_keys
    ADD CONSTRAINT chk_audit_signing_keys_key_id_format
        CHECK (key_id ~ '^audit-export:v[1-9][0-9]*$');
END IF;
END
$$;

-- -----------------------------------------------------
-- Helpful indexes
-- -----------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_audit_signing_keys_active
    ON audit_signing_keys (active);

CREATE INDEX IF NOT EXISTS idx_audit_signing_keys_expires_at
    ON audit_signing_keys (expires_at);

-- -----------------------------------------------------
-- At most ONE active key
-- -----------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS ux_audit_signing_keys_active_true
    ON audit_signing_keys (active)
    WHERE active = true;