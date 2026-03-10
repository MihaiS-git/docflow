/* =========================================================
   ADDITIONAL CORE INDEXES
   ========================================================= */

-- invariant: single active signing key
CREATE UNIQUE INDEX ux_audit_signing_keys_active_true
    ON audit_signing_keys (active)
    WHERE active = true;

-- chain state lookup
CREATE INDEX idx_audit_chain_state_stream
    ON audit_chain_state(stream);

/* =========================================================
   ADMIN AUDIT EVENTS
   ========================================================= */

-- main query index
CREATE INDEX idx_admin_audit_query
    ON admin_audit_events (timestamp DESC, id DESC)
    INCLUDE (tenant_id, actor_user_id, subject_id);

-- correlation lookup
CREATE INDEX idx_admin_audit_correlation
    ON admin_audit_events (correlation_id);

-- BRIN for time-range scans
CREATE INDEX brin_admin_audit_timestamp
    ON admin_audit_events
    USING BRIN (timestamp);

-- chain verification
CREATE INDEX idx_admin_chain_verify
    ON admin_audit_events (tenant_id, timestamp, id);

-- segment verification
CREATE INDEX idx_admin_chain_segment
    ON admin_audit_events (tenant_id, timestamp, id)
    WHERE chain_segment_hash IS NOT NULL;

-- event hash lookup (required for chain verification join)
CREATE INDEX idx_admin_event_hash
    ON admin_audit_events (event_hash);


/* =========================================================
   AUTHENTICATION EVENTS
   ========================================================= */

-- query index
CREATE INDEX idx_auth_events_query
    ON authentication_events (timestamp DESC, id DESC)
    INCLUDE (username, subject_id);

-- correlation lookup
CREATE INDEX idx_auth_events_correlation
    ON authentication_events (correlation_id);

-- BRIN
CREATE INDEX brin_auth_events_timestamp
    ON authentication_events
    USING BRIN (timestamp);

-- chain verification
CREATE INDEX idx_auth_chain_verify
    ON authentication_events (subject_id, timestamp, id);

-- segment verification
CREATE INDEX idx_auth_chain_segment
    ON authentication_events (subject_id, timestamp, id)
    WHERE chain_segment_hash IS NOT NULL;

-- event hash lookup
CREATE INDEX idx_auth_event_hash
    ON authentication_events (event_hash);


/* =========================================================
   CREDENTIAL LIFECYCLE AUDIT EVENTS
   ========================================================= */

-- query index
CREATE INDEX idx_credential_lifecycle_query
    ON credential_lifecycle_audit_events (timestamp DESC, id DESC)
    INCLUDE (subject_external_id);

-- correlation lookup
CREATE INDEX idx_credential_lifecycle_corr
    ON credential_lifecycle_audit_events (correlation_id);

-- BRIN
CREATE INDEX brin_credential_lifecycle_timestamp
    ON credential_lifecycle_audit_events
    USING BRIN (timestamp);

-- chain verification
CREATE INDEX idx_credential_chain_verify
    ON credential_lifecycle_audit_events (subject_external_id, timestamp, id);

-- segment verification
CREATE INDEX idx_credential_chain_segment
    ON credential_lifecycle_audit_events (subject_external_id, timestamp, id)
    WHERE chain_segment_hash IS NOT NULL;

-- event hash lookup
CREATE INDEX idx_credential_event_hash
    ON credential_lifecycle_audit_events (event_hash);


/* =========================================================
   IDENTITY PROJECTION AUDIT EVENTS
   ========================================================= */

-- query index
CREATE INDEX idx_identity_projection_query
    ON identity_projection_audit_events (timestamp DESC, id DESC)
    INCLUDE (subject_id);

-- correlation lookup
CREATE INDEX idx_identity_proj_corr
    ON identity_projection_audit_events (correlation_id);

-- BRIN
CREATE INDEX brin_identity_projection_timestamp
    ON identity_projection_audit_events
    USING BRIN (timestamp);

-- chain verification
CREATE INDEX idx_identity_chain_verify
    ON identity_projection_audit_events (subject_id, timestamp, id);

-- segment verification
CREATE INDEX idx_identity_chain_segment
    ON identity_projection_audit_events (subject_id, timestamp, id)
    WHERE chain_segment_hash IS NOT NULL;

-- event hash lookup
CREATE INDEX idx_identity_projection_event_hash
    ON identity_projection_audit_events (event_hash);


/* =========================================================
   LIFECYCLE DENIED AUDIT EVENTS
   ========================================================= */

-- query index
CREATE INDEX idx_lifecycle_denied_query
    ON lifecycle_denied_audit_events (timestamp DESC, id DESC)
    INCLUDE (subject_id);

-- correlation lookup
CREATE INDEX idx_lifecycle_denied_corr
    ON lifecycle_denied_audit_events (correlation_id);

-- BRIN
CREATE INDEX brin_lifecycle_denied_timestamp
    ON lifecycle_denied_audit_events
    USING BRIN (timestamp);

-- chain verification
CREATE INDEX idx_lifecycle_chain_verify
    ON lifecycle_denied_audit_events (subject_id, timestamp, id);

-- segment verification
CREATE INDEX idx_lifecycle_chain_segment
    ON lifecycle_denied_audit_events (subject_id, timestamp, id)
    WHERE chain_segment_hash IS NOT NULL;

-- event hash lookup
CREATE INDEX idx_lifecycle_denied_event_hash
    ON lifecycle_denied_audit_events (event_hash);


/* =========================================================
   RBAC DENIED AUDIT EVENTS
   ========================================================= */

-- query index
CREATE INDEX idx_rbac_denied_query
    ON rbac_denied_audit_events (timestamp DESC, id DESC)
    INCLUDE (subject_id);

-- correlation lookup
CREATE INDEX idx_rbac_denied_corr
    ON rbac_denied_audit_events (correlation_id);

-- BRIN
CREATE INDEX brin_rbac_denied_timestamp
    ON rbac_denied_audit_events
    USING BRIN (timestamp);

-- chain verification
CREATE INDEX idx_rbac_chain_verify
    ON rbac_denied_audit_events (subject_id, timestamp, id);

-- segment verification
CREATE INDEX idx_rbac_chain_segment
    ON rbac_denied_audit_events (subject_id, timestamp, id)
    WHERE chain_segment_hash IS NOT NULL;

-- event hash lookup
CREATE INDEX idx_rbac_denied_event_hash
    ON rbac_denied_audit_events (event_hash);


/* =========================================================
   ONBOARDING AUDIT EVENTS
   ========================================================= */

-- query index
CREATE INDEX idx_onboarding_query
    ON onboarding_audit_events (timestamp DESC, id DESC)
    INCLUDE (tenant_id, subject_id);

-- invite lookup
CREATE INDEX idx_onboarding_invite
    ON onboarding_audit_events (invite_id);

-- correlation lookup
CREATE INDEX idx_onboarding_corr
    ON onboarding_audit_events (correlation_id);

-- BRIN
CREATE INDEX brin_onboarding_timestamp
    ON onboarding_audit_events
    USING BRIN (timestamp);

-- chain verification
CREATE INDEX idx_onboarding_chain_verify
    ON onboarding_audit_events (tenant_id, timestamp, id);

-- segment verification
CREATE INDEX idx_onboarding_chain_segment
    ON onboarding_audit_events (tenant_id, timestamp, id)
    WHERE chain_segment_hash IS NOT NULL;

-- event hash lookup
CREATE INDEX idx_onboarding_event_hash
    ON onboarding_audit_events (event_hash);


/* =========================================================
   SENSITIVE ACCESS AUDIT EVENTS
   ========================================================= */

-- query index
CREATE INDEX idx_sensitive_access_query
    ON sensitive_access_audit_events (timestamp DESC, id DESC)
    INCLUDE (tenant_id, actor_user_id, subject_id);

-- correlation lookup
CREATE INDEX idx_sensitive_access_corr
    ON sensitive_access_audit_events (correlation_id);

-- BRIN
CREATE INDEX brin_sensitive_access_timestamp
    ON sensitive_access_audit_events
    USING BRIN (timestamp);

-- chain verification
CREATE INDEX idx_sensitive_chain_verify
    ON sensitive_access_audit_events (tenant_id, timestamp, id);

-- segment verification
CREATE INDEX idx_sensitive_chain_segment
    ON sensitive_access_audit_events (tenant_id, timestamp, id)
    WHERE chain_segment_hash IS NOT NULL;

-- event hash lookup
CREATE INDEX idx_sensitive_access_event_hash
    ON sensitive_access_audit_events (event_hash);


/* =========================================================
   UNAUTHENTICATED ACCESS AUDIT EVENTS
   ========================================================= */

-- query index
CREATE INDEX idx_unauth_access_query
    ON unauthenticated_access_audit_events (timestamp DESC, id DESC);

-- correlation lookup
CREATE INDEX idx_unauth_access_corr
    ON unauthenticated_access_audit_events (correlation_id);

-- BRIN
CREATE INDEX brin_unauth_access_timestamp
    ON unauthenticated_access_audit_events
    USING BRIN (timestamp);

-- chain verification
CREATE INDEX idx_unauth_chain_verify
    ON unauthenticated_access_audit_events (timestamp, id);

-- segment verification
CREATE INDEX idx_unauth_chain_segment
    ON unauthenticated_access_audit_events (timestamp, id)
    WHERE chain_segment_hash IS NOT NULL;

-- event hash lookup
CREATE INDEX idx_unauth_access_event_hash
    ON unauthenticated_access_audit_events (event_hash);

/* =========================================================
   OTHERS
   ========================================================= */
CREATE INDEX idx_invites_expired_pending
    ON invites (expires_at)
    WHERE status = 'PENDING';

-- prevents multiple identical invites
CREATE UNIQUE INDEX ux_invites_pending_email
    ON invites (tenant_id, lower(email))
    WHERE status = 'PENDING';
