CREATE OR REPLACE FUNCTION audit_prevent_modification()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- allow retention job to delete rows
    IF TG_OP = 'DELETE'
       AND current_setting('docflow.retention_mode', true) = 'on'
    THEN
        RETURN OLD;
END IF;

    RAISE EXCEPTION
        'Audit tables are immutable: operation % is not allowed',
        TG_OP;
END;
$$;


DROP TRIGGER IF EXISTS trg_admin_audit_immutable
ON admin_audit_events;

CREATE TRIGGER trg_admin_audit_immutable
    BEFORE UPDATE OR DELETE ON admin_audit_events
FOR EACH ROW EXECUTE FUNCTION audit_prevent_modification();


DROP TRIGGER IF EXISTS trg_authentication_immutable
ON authentication_events;

CREATE TRIGGER trg_authentication_immutable
    BEFORE UPDATE OR DELETE ON authentication_events
FOR EACH ROW EXECUTE FUNCTION audit_prevent_modification();


DROP TRIGGER IF EXISTS trg_credential_lifecycle_immutable
ON credential_lifecycle_audit_events;

CREATE TRIGGER trg_credential_lifecycle_immutable
    BEFORE UPDATE OR DELETE ON credential_lifecycle_audit_events
FOR EACH ROW EXECUTE FUNCTION audit_prevent_modification();


DROP TRIGGER IF EXISTS trg_identity_projection_immutable
ON identity_projection_audit_events;

CREATE TRIGGER trg_identity_projection_immutable
    BEFORE UPDATE OR DELETE ON identity_projection_audit_events
FOR EACH ROW EXECUTE FUNCTION audit_prevent_modification();


DROP TRIGGER IF EXISTS trg_lifecycle_denied_immutable
ON lifecycle_denied_audit_events;

CREATE TRIGGER trg_lifecycle_denied_immutable
    BEFORE UPDATE OR DELETE ON lifecycle_denied_audit_events
FOR EACH ROW EXECUTE FUNCTION audit_prevent_modification();


DROP TRIGGER IF EXISTS trg_rbac_denied_immutable
ON rbac_denied_audit_events;

CREATE TRIGGER trg_rbac_denied_immutable
    BEFORE UPDATE OR DELETE ON rbac_denied_audit_events
FOR EACH ROW EXECUTE FUNCTION audit_prevent_modification();


DROP TRIGGER IF EXISTS trg_onboarding_immutable
ON onboarding_audit_events;

CREATE TRIGGER trg_onboarding_immutable
    BEFORE UPDATE OR DELETE ON onboarding_audit_events
FOR EACH ROW EXECUTE FUNCTION audit_prevent_modification();


DROP TRIGGER IF EXISTS trg_sensitive_access_immutable
ON sensitive_access_audit_events;

CREATE TRIGGER trg_sensitive_access_immutable
    BEFORE UPDATE OR DELETE ON sensitive_access_audit_events
FOR EACH ROW EXECUTE FUNCTION audit_prevent_modification();


DROP TRIGGER IF EXISTS trg_unauth_access_immutable
ON unauthenticated_access_audit_events;

CREATE TRIGGER trg_unauth_access_immutable
    BEFORE UPDATE OR DELETE ON unauthenticated_access_audit_events
FOR EACH ROW EXECUTE FUNCTION audit_prevent_modification();