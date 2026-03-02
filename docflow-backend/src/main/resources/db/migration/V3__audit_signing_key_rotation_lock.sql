CREATE TABLE IF NOT EXISTS audit_signing_key_rotation_lock (
      id BIGINT PRIMARY KEY,
      CONSTRAINT chk_audit_signing_key_rotation_lock_single_row CHECK (id = 1)
    );

INSERT INTO audit_signing_key_rotation_lock (id)
VALUES (1)
    ON CONFLICT (id) DO NOTHING;