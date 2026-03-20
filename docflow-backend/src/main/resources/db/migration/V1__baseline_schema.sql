CREATE TABLE public.users
(
    id                    uuid PRIMARY KEY,

    business_phone        varchar(255),
    created_at            timestamptz  NOT NULL,
    department            varchar(255),

    display_name          varchar(255) NOT NULL,
    email                 varchar(320) NOT NULL,

    external_subject_id   varchar(128),

    first_name            varchar(255) NOT NULL,
    job_title             varchar(255),
    last_name             varchar(255) NOT NULL,

    last_login_at         timestamptz,
    last_login_ip         varchar(255),
    last_login_user_agent varchar(512),

    status                varchar(16)  NOT NULL,
    updated_at            timestamptz  NOT NULL,

    CONSTRAINT users_status_check
        CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_external_subject UNIQUE (external_subject_id)
);

CREATE TABLE public.tenants
(
    id                uuid PRIMARY KEY,

    bootstrap_enabled boolean      NOT NULL,
    created_at        timestamptz  NOT NULL,

    data_region       varchar(512),

    name              varchar(128) NOT NULL,

    retention_days    integer,

    status            varchar(16)  NOT NULL,
    tenant_type       varchar(32)  NOT NULL,

    updated_at        timestamptz  NOT NULL,

    CONSTRAINT uk_tenants_name UNIQUE (name),

    CONSTRAINT tenants_status_check
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),

    CONSTRAINT tenants_tenant_type_check
        CHECK (tenant_type IN ('ROOT', 'ORGANIZATION'))
);

CREATE TABLE public.user_tenant_memberships
(
    id         uuid PRIMARY KEY,

    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,

    role       varchar(32) NOT NULL,
    status     varchar(16) NOT NULL,

    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    user_id    uuid        NOT NULL REFERENCES users (id),

    CONSTRAINT uk_membership_user_tenant UNIQUE (user_id, tenant_id),

    CONSTRAINT membership_role_check
        CHECK (role IN ('MEMBER', 'EXECUTOR', 'REVIEWER', 'MANAGER')),

    CONSTRAINT membership_status_check
        CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE public.invites
(
    id           uuid PRIMARY KEY,

    created_at   timestamptz  NOT NULL,
    expires_at   timestamptz  NOT NULL,

    email        varchar(320) NOT NULL,

    first_name   varchar(255) NOT NULL,
    last_name    varchar(255) NOT NULL,

    job_title    varchar(255),
    department   varchar(255),

    status       varchar(16)  NOT NULL,
    tenant_role  varchar(32),

    hashed_token varchar(64)  NOT NULL,

    tenant_id    uuid         NOT NULL REFERENCES tenants (id),
    user_id      uuid REFERENCES users (id),

    CONSTRAINT uk_invites_hashed_token UNIQUE (hashed_token),

    CONSTRAINT invites_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED')),

    CONSTRAINT invites_tenant_role_check
        CHECK (tenant_role IN ('MEMBER', 'EXECUTOR', 'EXECUTOR', 'MANAGER'))
);

CREATE TABLE public.audit_signing_keys
(
    key_id                 varchar(128) PRIMARY KEY,

    active                 boolean     NOT NULL,
    created_at             timestamptz NOT NULL,

    encrypted_private_key  bytea       NOT NULL,
    public_key_pem         text        NOT NULL,

    expires_at             timestamptz NOT NULL,

    fingerprint_sha256_hex varchar(64) NOT NULL,

    CONSTRAINT uk_audit_signing_keys_fingerprint UNIQUE (fingerprint_sha256_hex)
);

CREATE TABLE public.audit_export_snapshot
(
    id                uuid PRIMARY KEY,

    timestamp         timestamptz   NOT NULL,
    created_by        uuid          NOT NULL REFERENCES users (id),

    stream            varchar(128)  NOT NULL,
    tenant_id         uuid REFERENCES tenants (id),

    from_ts           timestamptz   NOT NULL,
    to_ts             timestamptz   NOT NULL,

    row_count         bigint        NOT NULL,

    sha256_digest_hex varchar(64)   NOT NULL,

    signature_alg     varchar(64)   NOT NULL,
    signature_b64     varchar(1024) NOT NULL,

    key_id            varchar(128)
        REFERENCES audit_signing_keys (key_id)
            ON DELETE RESTRICT,

    CONSTRAINT audit_export_snapshot_stream_check
        CHECK (stream IN (
                          'ADMIN',
                          'AUTHENTICATION',
                          'CREDENTIAL_LIFECYCLE',
                          'IDENTITY_PROJECTION',
                          'LIFECYCLE_DENIED',
                          'RBAC_DENIED',
                          'ONBOARDING',
                          'SENSITIVE_ACCESS',
                          'UNAUTHENTICATED_ACCESS'
            ))
);

CREATE TABLE public.audit_retention_policies
(
    id              uuid PRIMARY KEY,

    archive_enabled boolean      NOT NULL,

    retention_days  integer      NOT NULL,

    stream_name     varchar(128) NOT NULL,

    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,

    CONSTRAINT uk_audit_retention_policy_stream
        UNIQUE (stream_name),
    CONSTRAINT audit_retention_policy_stream_check
        CHECK (stream_name IN (
                               'ADMIN',
                               'AUTHENTICATION',
                               'CREDENTIAL_LIFECYCLE',
                               'IDENTITY_PROJECTION',
                               'LIFECYCLE_DENIED',
                               'RBAC_DENIED',
                               'ONBOARDING',
                               'SENSITIVE_ACCESS',
                               'UNAUTHENTICATED_ACCESS'
            ))
);

CREATE TABLE public.audit_chain_state
(
    state_key               varchar(256) PRIMARY KEY,

    stream                  varchar(128) NOT NULL,
    tenant_id               uuid REFERENCES tenants (id),

    last_event_hash         varchar(128) NOT NULL,
    last_checkpoint_hash    varchar(128),

    last_checkpoint_at      timestamptz,

    events_since_checkpoint integer      NOT NULL,
    event_count             bigint       NOT NULL,

    updated_at              timestamptz  NOT NULL,

    CONSTRAINT audit_chain_state_stream_check
        CHECK (stream IN (
                          'ADMIN',
                          'AUTHENTICATION',
                          'CREDENTIAL_LIFECYCLE',
                          'IDENTITY_PROJECTION',
                          'LIFECYCLE_DENIED',
                          'RBAC_DENIED',
                          'ONBOARDING',
                          'SENSITIVE_ACCESS',
                          'UNAUTHENTICATED_ACCESS'
            ))
);

CREATE TABLE public.audit_chain_checkpoints
(
    stream               varchar(128) PRIMARY KEY,
    last_event_timestamp timestamptz NOT NULL
);

CREATE TABLE public.keycloak_event_checkpoint
(
    id                 varchar(255) PRIMARY KEY,
    last_event_time_ms bigint NOT NULL
);

CREATE TABLE public.user_identity_projection
(
    subject_id     varchar(255) PRIMARY KEY,

    display_name   varchar(255),
    email          varchar(255),

    username       varchar(255),

    source         varchar(255) NOT NULL,

    last_synced_at timestamptz  NOT NULL
);

CREATE TABLE public.authentication_events
(
    id                    uuid PRIMARY KEY,

    authentication_result varchar(32)  NOT NULL,

    chain_segment_hash    varchar(128),
    chain_version         integer      NOT NULL,

    correlation_id        varchar(128) NOT NULL,
    correlation_source    varchar(32)  NOT NULL,

    event_fingerprint     varchar(128) NOT NULL,
    event_hash            varchar(128) NOT NULL,

    execution_context     varchar(32)  NOT NULL,

    idp                   varchar(64)  NOT NULL,

    ip                    varchar(128) NOT NULL,
    metadata              jsonb,

    prev_event_hash       varchar(128) NOT NULL,

    audit_result          varchar(16)  NOT NULL,

    source                varchar(64)  NOT NULL,

    subject_id            varchar(128) NOT NULL,

    "timestamp"           timestamptz  NOT NULL,

    user_agent            varchar(512) NOT NULL,

    username              varchar(128) NOT NULL,

    CONSTRAINT uk_authentication_event_fingerprint UNIQUE (event_fingerprint),

    CONSTRAINT authentication_events_audit_result_check
        CHECK (audit_result IN ('SUCCESS', 'DENIED', 'FAILED')),

    CONSTRAINT authentication_events_authentication_result_check
        CHECK (authentication_result IN ('SUCCESS', 'FAILURE', 'LOGOUT')),

    CONSTRAINT authentication_events_execution_context_check
        CHECK (execution_context IN ('HTTP', 'SCHEDULED_JOB', 'AUTH_FLOW', 'ADMIN_API', 'SYSTEM')),

    CONSTRAINT authentication_events_correlation_source_check
        CHECK (correlation_source IN ('REQUEST_ID', 'GENERATED', 'ADMIN_EVENT_ID', 'SESSION_ID', 'PULL_RUN'))
);

CREATE TABLE public.admin_audit_events
(
    id                 uuid PRIMARY KEY,

    action_type        varchar(64)  NOT NULL,

    actor_user_id      uuid         NOT NULL REFERENCES users (id),
    target_user_id     uuid REFERENCES users (id),

    chain_segment_hash varchar(128),
    chain_version      integer      NOT NULL,

    correlation_id     varchar(128) NOT NULL,
    correlation_source varchar(32)  NOT NULL,

    event_fingerprint  varchar(128) NOT NULL,
    event_hash         varchar(128) NOT NULL,

    execution_context  varchar(32)  NOT NULL,

    ip                 varchar(128) NOT NULL,
    metadata           jsonb,

    prev_event_hash    varchar(128) NOT NULL,

    result             varchar(16)  NOT NULL,

    subject_id         varchar(128) NOT NULL,

    tenant_id          uuid         NOT NULL REFERENCES tenants (id),

    "timestamp"        timestamptz  NOT NULL,

    user_agent         varchar(512) NOT NULL,

    CONSTRAINT uk_admin_audit_event_fingerprint UNIQUE (event_fingerprint),

    CONSTRAINT admin_audit_events_result_check
        CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED'))
);

CREATE TABLE public.credential_lifecycle_audit_events
(
    id                  uuid PRIMARY KEY,

    chain_segment_hash  varchar(128),
    chain_version       integer      NOT NULL,

    client_id           varchar(128),

    correlation_id      varchar(128) NOT NULL,
    correlation_source  varchar(32)  NOT NULL,

    event_fingerprint   varchar(128) NOT NULL,
    event_hash          varchar(128) NOT NULL,

    event_type          varchar(64)  NOT NULL,

    execution_context   varchar(32)  NOT NULL,

    ip                  varchar(128) NOT NULL,
    metadata            jsonb,

    prev_event_hash     varchar(128) NOT NULL,

    reason_code         varchar(64)  NOT NULL,
    reason_detail       varchar(512),

    required_action     varchar(128),

    result              varchar(16)  NOT NULL,

    session_id          varchar(128),

    subject_external_id varchar(128),

    "timestamp"         timestamptz  NOT NULL,

    CONSTRAINT uk_credential_lifecycle_event_fingerprint
        UNIQUE (event_fingerprint),

    CONSTRAINT credential_lifecycle_event_type_check
        CHECK (event_type IN (
                              'PASSWORD_CHANGED',
                              'PASSWORD_RESET',
                              'MFA_ENROLLED',
                              'MFA_REMOVED',
                              'REQUIRED_ACTION_SET',
                              'REQUIRED_ACTION_CLEARED',
                              'UNKNOWN'
            )),

    CONSTRAINT credential_lifecycle_result_check
        CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),

    CONSTRAINT credential_lifecycle_execution_context_check
        CHECK (execution_context IN (
                                     'HTTP', 'SCHEDULED_JOB', 'AUTH_FLOW', 'ADMIN_API', 'SYSTEM'
            )),

    CONSTRAINT credential_lifecycle_correlation_source_check
        CHECK (correlation_source IN (
                                      'REQUEST_ID', 'GENERATED', 'ADMIN_EVENT_ID', 'SESSION_ID', 'PULL_RUN'
            ))
);

CREATE TABLE public.identity_projection_audit_events
(
    id                 uuid PRIMARY KEY,

    chain_segment_hash varchar(128),
    chain_version      integer      NOT NULL,

    correlation_id     varchar(128) NOT NULL,
    correlation_source varchar(32)  NOT NULL,

    event_fingerprint  varchar(128) NOT NULL,
    event_hash         varchar(128) NOT NULL,

    execution_context  varchar(32)  NOT NULL,

    metadata           jsonb,

    prev_event_hash    varchar(128) NOT NULL,

    reason_code        varchar(64)  NOT NULL,

    result             varchar(16)  NOT NULL,

    subject_id         varchar(128) NOT NULL,

    "timestamp"        timestamptz  NOT NULL,

    CONSTRAINT uk_identity_projection_event_fingerprint
        UNIQUE (event_fingerprint),

    CONSTRAINT identity_projection_result_check
        CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),

    CONSTRAINT identity_projection_execution_context_check
        CHECK (execution_context IN (
                                     'HTTP', 'SCHEDULED_JOB', 'AUTH_FLOW', 'ADMIN_API', 'SYSTEM'
            )),

    CONSTRAINT identity_projection_correlation_source_check
        CHECK (correlation_source IN (
                                      'REQUEST_ID', 'GENERATED', 'ADMIN_EVENT_ID', 'SESSION_ID', 'PULL_RUN'
            ))
);

CREATE TABLE public.lifecycle_denied_audit_events
(
    id                 uuid PRIMARY KEY,

    chain_segment_hash varchar(128),
    chain_version      integer      NOT NULL,

    correlation_id     varchar(128) NOT NULL,
    correlation_source varchar(32)  NOT NULL,

    event_fingerprint  varchar(128) NOT NULL,
    event_hash         varchar(128) NOT NULL,

    execution_context  varchar(32)  NOT NULL,

    http_method        varchar(16)  NOT NULL,

    ip                 varchar(128) NOT NULL,
    metadata           jsonb,

    path               varchar(512) NOT NULL,

    prev_event_hash    varchar(128) NOT NULL,

    reason_code        varchar(64)  NOT NULL,

    result             varchar(16)  NOT NULL,

    subject_id         varchar(128) NOT NULL,

    "timestamp"        timestamptz  NOT NULL,

    user_agent         varchar(512) NOT NULL,

    CONSTRAINT uk_lifecycle_denied_event_fingerprint UNIQUE (event_fingerprint),

    CONSTRAINT lifecycle_denied_result_check
        CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),

    CONSTRAINT lifecycle_denied_execution_context_check
        CHECK (execution_context IN (
                                     'HTTP', 'SCHEDULED_JOB', 'AUTH_FLOW', 'ADMIN_API', 'SYSTEM'
            )),

    CONSTRAINT lifecycle_denied_correlation_source_check
        CHECK (correlation_source IN (
                                      'REQUEST_ID', 'GENERATED', 'ADMIN_EVENT_ID', 'SESSION_ID', 'PULL_RUN'
            ))
);

CREATE TABLE public.rbac_denied_audit_events
(
    id                 uuid PRIMARY KEY,

    chain_segment_hash varchar(128),
    chain_version      integer      NOT NULL,

    correlation_id     varchar(128) NOT NULL,
    correlation_source varchar(32)  NOT NULL,

    event_fingerprint  varchar(128) NOT NULL,
    event_hash         varchar(128) NOT NULL,

    execution_context  varchar(32)  NOT NULL,

    http_method        varchar(16)  NOT NULL,

    ip                 varchar(128) NOT NULL,
    metadata           jsonb,

    path               varchar(512) NOT NULL,

    prev_event_hash    varchar(128) NOT NULL,

    result             varchar(16)  NOT NULL,

    subject_id         varchar(128) NOT NULL,

    "timestamp"        timestamptz  NOT NULL,

    user_agent         varchar(512) NOT NULL,

    CONSTRAINT uk_rbac_denied_event_fingerprint UNIQUE (event_fingerprint),

    CONSTRAINT rbac_denied_result_check
        CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),

    CONSTRAINT rbac_denied_execution_context_check
        CHECK (execution_context IN (
                                     'HTTP', 'SCHEDULED_JOB', 'AUTH_FLOW', 'ADMIN_API', 'SYSTEM'
            )),

    CONSTRAINT rbac_denied_correlation_source_check
        CHECK (correlation_source IN (
                                      'REQUEST_ID', 'GENERATED', 'ADMIN_EVENT_ID', 'SESSION_ID', 'PULL_RUN'
            ))
);

CREATE TABLE public.onboarding_audit_events
(
    id                 uuid PRIMARY KEY,

    actor_user_id      uuid REFERENCES users (id),

    chain_segment_hash varchar(128),
    chain_version      integer      NOT NULL,

    correlation_id     varchar(128) NOT NULL,
    correlation_source varchar(32)  NOT NULL,

    event_fingerprint  varchar(128) NOT NULL,
    event_hash         varchar(128) NOT NULL,

    execution_context  varchar(32)  NOT NULL,

    invite_id          uuid         NOT NULL REFERENCES invites (id),

    ip                 varchar(128) NOT NULL,
    metadata           jsonb,

    outcome            varchar(32)  NOT NULL,

    prev_event_hash    varchar(128) NOT NULL,

    reason_code        varchar(64)  NOT NULL,
    reason_detail      varchar(512),

    result             varchar(16)  NOT NULL,

    subject_id         varchar(128) NOT NULL,

    tenant_id          uuid         NOT NULL REFERENCES tenants (id),

    "timestamp"        timestamptz  NOT NULL,

    user_agent         varchar(512) NOT NULL,

    CONSTRAINT uk_onboarding_event_fingerprint UNIQUE (event_fingerprint),

    CONSTRAINT onboarding_outcome_check
        CHECK (outcome IN ('SUCCESS', 'FAILURE')),

    CONSTRAINT onboarding_result_check
        CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),

    CONSTRAINT onboarding_execution_context_check
        CHECK (execution_context IN (
                                     'HTTP', 'SCHEDULED_JOB', 'AUTH_FLOW', 'ADMIN_API', 'SYSTEM'
            )),

    CONSTRAINT onboarding_correlation_source_check
        CHECK (correlation_source IN (
                                      'REQUEST_ID', 'GENERATED', 'ADMIN_EVENT_ID', 'SESSION_ID', 'PULL_RUN'
            ))
);

CREATE TABLE public.sensitive_access_audit_events
(
    id                        uuid PRIMARY KEY,

    action                    varchar(64)  NOT NULL,

    actor_external_subject_id varchar(128),
    actor_user_id             uuid REFERENCES users (id),

    chain_segment_hash        varchar(128),
    chain_version             integer      NOT NULL,

    correlation_id            varchar(128) NOT NULL,
    correlation_source        varchar(32)  NOT NULL,

    data_classification       varchar(32)  NOT NULL,

    event_fingerprint         varchar(128) NOT NULL,
    event_hash                varchar(128) NOT NULL,

    execution_context         varchar(32)  NOT NULL,

    ip                        varchar(128) NOT NULL,
    metadata                  jsonb,

    prev_event_hash           varchar(128) NOT NULL,

    reason_code               varchar(64)  NOT NULL,
    reason_detail             varchar(512),

    resource                  varchar(128) NOT NULL,
    resource_path             varchar(512),

    result                    varchar(16)  NOT NULL,

    subject_id                varchar(128) NOT NULL,

    subject_type              varchar(64)  NOT NULL,

    tenant_id                 uuid         NOT NULL REFERENCES tenants (id),

    "timestamp"               timestamptz  NOT NULL,

    user_agent                varchar(512) NOT NULL,

    CONSTRAINT uk_sensitive_access_event_fingerprint UNIQUE (event_fingerprint),

    CONSTRAINT sensitive_access_data_classification_check
        CHECK (data_classification IN ('INTERNAL', 'CONFIDENTIAL', 'RESTRICTED', 'REGULATED')),

    CONSTRAINT sensitive_access_subject_type_check
        CHECK (subject_type IN (
                                'USER', 'TENANT', 'AUDIT_STREAM', 'RBAC_TOPOLOGY', 'SECURITY_CONFIGURATION', 'SYSTEM'
            )),

    CONSTRAINT sensitive_access_result_check
        CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),

    CONSTRAINT sensitive_access_execution_context_check
        CHECK (execution_context IN (
                                     'HTTP', 'SCHEDULED_JOB', 'AUTH_FLOW', 'ADMIN_API', 'SYSTEM'
            )),

    CONSTRAINT sensitive_access_correlation_source_check
        CHECK (correlation_source IN (
                                      'REQUEST_ID', 'GENERATED', 'ADMIN_EVENT_ID', 'SESSION_ID', 'PULL_RUN'
            ))
);

CREATE TABLE public.unauthenticated_access_audit_events
(
    id                 uuid PRIMARY KEY,

    chain_segment_hash varchar(128),
    chain_version      integer      NOT NULL,

    correlation_id     varchar(128),
    correlation_source varchar(32)  NOT NULL,

    event_fingerprint  varchar(128) NOT NULL,
    event_hash         varchar(128) NOT NULL,

    execution_context  varchar(32)  NOT NULL,

    http_method        varchar(16)  NOT NULL,

    ip                 varchar(128),
    metadata           jsonb,

    path               varchar(512) NOT NULL,

    prev_event_hash    varchar(128) NOT NULL,

    result             varchar(16)  NOT NULL,

    "timestamp"        timestamptz  NOT NULL,

    user_agent         varchar(512),

    CONSTRAINT uk_unauth_access_event_fingerprint UNIQUE (event_fingerprint),

    CONSTRAINT unauth_access_result_check
        CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),

    CONSTRAINT unauth_access_execution_context_check
        CHECK (execution_context IN (
                                     'HTTP', 'SCHEDULED_JOB', 'AUTH_FLOW', 'ADMIN_API', 'SYSTEM'
            )),

    CONSTRAINT unauth_access_correlation_source_check
        CHECK (correlation_source IN (
                                      'REQUEST_ID', 'GENERATED', 'ADMIN_EVENT_ID', 'SESSION_ID', 'PULL_RUN'
            ))
);

/* =========================================================
   CORE
   ========================================================= */

-- invariant: single active signing key
CREATE UNIQUE INDEX ux_audit_signing_keys_active_true
    ON audit_signing_keys (active) WHERE active = true;

-- chain state lookup
CREATE INDEX idx_audit_chain_state_stream
    ON audit_chain_state (stream);


CREATE INDEX idx_audit_chain_state_state_key_last_event_hash
    ON audit_chain_state (state_key, last_event_hash);


/* =========================================================
   USERS
   ========================================================= */

CREATE UNIQUE INDEX ux_users_email_lower
    ON users (lower(email));

CREATE INDEX idx_users_first_name_trgm
    ON users USING gin (first_name gin_trgm_ops);

CREATE INDEX idx_users_last_name_trgm
    ON users USING gin (last_name gin_trgm_ops);


/* =========================================================
   TENANTS
   ========================================================= */

CREATE INDEX idx_tenants_status_region_created_at
    ON tenants (status, LOWER(data_region), created_at DESC);

CREATE INDEX idx_tenants_data_region_lower
    ON tenants (LOWER(data_region));

CREATE INDEX idx_tenants_created_at
    ON tenants (created_at DESC);

CREATE INDEX idx_tenants_name_lower
    ON tenants (LOWER(name));

/* =========================================================
   MEMBERSHIPS
   ========================================================= */

CREATE INDEX idx_memberships_user
    ON user_tenant_memberships (user_id);

CREATE INDEX idx_memberships_tenant
    ON user_tenant_memberships (tenant_id);

CREATE INDEX idx_memberships_manager_lookup
    ON user_tenant_memberships (tenant_id, role, status, user_id);

CREATE INDEX idx_memberships_active_managers
    ON user_tenant_memberships (tenant_id, user_id) WHERE role = 'MANAGER'
  AND status = 'ACTIVE';


/* =========================================================
   INVITES
   ========================================================= */

CREATE INDEX idx_invites_tenant
    ON invites (tenant_id);

CREATE INDEX idx_invites_expired_pending
    ON invites (expires_at) WHERE status = 'PENDING';

CREATE UNIQUE INDEX ux_invites_pending_email
    ON invites (tenant_id, lower(email)) WHERE status = 'PENDING';

CREATE UNIQUE INDEX ux_invites_hashed_token
    ON invites (hashed_token);


/* =========================================================
   ADMIN AUDIT EVENTS
   ========================================================= */

CREATE INDEX idx_admin_audit_query
    ON admin_audit_events (timestamp DESC, id DESC) INCLUDE (tenant_id, actor_user_id, subject_id);

CREATE INDEX idx_admin_audit_correlation
    ON admin_audit_events (correlation_id);

CREATE INDEX idx_admin_chain_verify
    ON admin_audit_events (tenant_id, timestamp, id);

CREATE INDEX idx_admin_chain_segment
    ON admin_audit_events (tenant_id, timestamp, id) WHERE chain_segment_hash IS NOT NULL;

CREATE INDEX idx_admin_event_hash
    ON admin_audit_events (event_hash);

CREATE INDEX brin_admin_audit_timestamp
    ON admin_audit_events USING BRIN (timestamp);


/* =========================================================
   AUTHENTICATION EVENTS
   ========================================================= */

CREATE INDEX idx_auth_events_query
    ON authentication_events (timestamp DESC, id DESC) INCLUDE (username, subject_id);

CREATE INDEX idx_auth_events_correlation
    ON authentication_events (correlation_id);

CREATE INDEX idx_auth_chain_verify
    ON authentication_events (subject_id, timestamp, id);

CREATE INDEX idx_auth_chain_segment
    ON authentication_events (subject_id, timestamp, id) WHERE chain_segment_hash IS NOT NULL;

CREATE INDEX idx_auth_event_hash
    ON authentication_events (event_hash);

CREATE INDEX brin_auth_events_timestamp
    ON authentication_events USING BRIN (timestamp);


/* =========================================================
   CREDENTIAL LIFECYCLE AUDIT EVENTS
   ========================================================= */

CREATE INDEX idx_credential_lifecycle_query
    ON credential_lifecycle_audit_events (timestamp DESC, id DESC) INCLUDE (subject_external_id);

CREATE INDEX idx_credential_lifecycle_corr
    ON credential_lifecycle_audit_events (correlation_id);

CREATE INDEX idx_credential_chain_verify
    ON credential_lifecycle_audit_events (subject_external_id, timestamp, id);

CREATE INDEX idx_credential_chain_segment
    ON credential_lifecycle_audit_events (subject_external_id, timestamp, id) WHERE chain_segment_hash IS NOT NULL;

CREATE INDEX idx_credential_event_hash
    ON credential_lifecycle_audit_events (event_hash);

CREATE INDEX brin_credential_lifecycle_timestamp
    ON credential_lifecycle_audit_events USING BRIN (timestamp);


/* =========================================================
   IDENTITY PROJECTION AUDIT EVENTS
   ========================================================= */

CREATE INDEX idx_identity_projection_query
    ON identity_projection_audit_events (timestamp DESC, id DESC) INCLUDE (subject_id);

CREATE INDEX idx_identity_proj_corr
    ON identity_projection_audit_events (correlation_id);

CREATE INDEX idx_identity_chain_verify
    ON identity_projection_audit_events (subject_id, timestamp, id);

CREATE INDEX idx_identity_chain_segment
    ON identity_projection_audit_events (subject_id, timestamp, id) WHERE chain_segment_hash IS NOT NULL;

CREATE INDEX idx_identity_projection_event_hash
    ON identity_projection_audit_events (event_hash);

CREATE INDEX brin_identity_projection_timestamp
    ON identity_projection_audit_events USING BRIN (timestamp);


/* =========================================================
   LIFECYCLE DENIED AUDIT EVENTS
   ========================================================= */

CREATE INDEX idx_lifecycle_denied_query
    ON lifecycle_denied_audit_events (timestamp DESC, id DESC) INCLUDE (subject_id);

CREATE INDEX idx_lifecycle_denied_corr
    ON lifecycle_denied_audit_events (correlation_id);

CREATE INDEX idx_lifecycle_chain_verify
    ON lifecycle_denied_audit_events (subject_id, timestamp, id);

CREATE INDEX idx_lifecycle_chain_segment
    ON lifecycle_denied_audit_events (subject_id, timestamp, id) WHERE chain_segment_hash IS NOT NULL;

CREATE INDEX idx_lifecycle_denied_event_hash
    ON lifecycle_denied_audit_events (event_hash);

CREATE INDEX brin_lifecycle_denied_timestamp
    ON lifecycle_denied_audit_events USING BRIN (timestamp);


/* =========================================================
   RBAC DENIED AUDIT EVENTS
   ========================================================= */

CREATE INDEX idx_rbac_denied_query
    ON rbac_denied_audit_events (timestamp DESC, id DESC) INCLUDE (subject_id);

CREATE INDEX idx_rbac_denied_corr
    ON rbac_denied_audit_events (correlation_id);

CREATE INDEX idx_rbac_chain_verify
    ON rbac_denied_audit_events (subject_id, timestamp, id);

CREATE INDEX idx_rbac_chain_segment
    ON rbac_denied_audit_events (subject_id, timestamp, id) WHERE chain_segment_hash IS NOT NULL;

CREATE INDEX idx_rbac_denied_event_hash
    ON rbac_denied_audit_events (event_hash);

CREATE INDEX brin_rbac_denied_timestamp
    ON rbac_denied_audit_events USING BRIN (timestamp);


/* =========================================================
   ONBOARDING AUDIT EVENTS
   ========================================================= */

CREATE INDEX idx_onboarding_query
    ON onboarding_audit_events (timestamp DESC, id DESC) INCLUDE (tenant_id, subject_id);

CREATE INDEX idx_onboarding_invite
    ON onboarding_audit_events (invite_id);

CREATE INDEX idx_onboarding_corr
    ON onboarding_audit_events (correlation_id);

CREATE INDEX idx_onboarding_chain_verify
    ON onboarding_audit_events (tenant_id, timestamp, id);

CREATE INDEX idx_onboarding_chain_segment
    ON onboarding_audit_events (tenant_id, timestamp, id) WHERE chain_segment_hash IS NOT NULL;

CREATE INDEX idx_onboarding_event_hash
    ON onboarding_audit_events (event_hash);

CREATE INDEX brin_onboarding_timestamp
    ON onboarding_audit_events USING BRIN (timestamp);

CREATE INDEX idx_onboarding_tenant
    ON onboarding_audit_events (tenant_id);


/* =========================================================
   SENSITIVE ACCESS AUDIT EVENTS
   ========================================================= */

CREATE INDEX idx_sensitive_access_query
    ON sensitive_access_audit_events (timestamp DESC, id DESC) INCLUDE (tenant_id, actor_user_id, subject_id);

CREATE INDEX idx_sensitive_access_corr
    ON sensitive_access_audit_events (correlation_id);

CREATE INDEX idx_sensitive_chain_verify
    ON sensitive_access_audit_events (tenant_id, timestamp, id);

CREATE INDEX idx_sensitive_chain_segment
    ON sensitive_access_audit_events (tenant_id, timestamp, id) WHERE chain_segment_hash IS NOT NULL;

CREATE INDEX idx_sensitive_access_event_hash
    ON sensitive_access_audit_events (event_hash);

CREATE INDEX brin_sensitive_access_timestamp
    ON sensitive_access_audit_events USING BRIN (timestamp);

CREATE INDEX idx_sensitive_access_tenant
    ON sensitive_access_audit_events (tenant_id);


/* =========================================================
   UNAUTHENTICATED ACCESS AUDIT EVENTS
   ========================================================= */

CREATE INDEX idx_unauth_access_query
    ON unauthenticated_access_audit_events (timestamp DESC, id DESC);

CREATE INDEX idx_unauth_access_corr
    ON unauthenticated_access_audit_events (correlation_id);

CREATE INDEX idx_unauth_chain_verify
    ON unauthenticated_access_audit_events (timestamp, id);

CREATE INDEX idx_unauth_chain_segment
    ON unauthenticated_access_audit_events (timestamp, id) WHERE chain_segment_hash IS NOT NULL;

CREATE INDEX idx_unauth_access_event_hash
    ON unauthenticated_access_audit_events (event_hash);

CREATE INDEX brin_unauth_access_timestamp
    ON unauthenticated_access_audit_events USING BRIN (timestamp);


/* =========================================================
   EXPORT SNAPSHOTS
   ========================================================= */

CREATE INDEX idx_audit_export_snapshot_ts_id
    ON audit_export_snapshot ("timestamp" DESC, id DESC);

CREATE INDEX idx_audit_export_snapshot_ts_id
    ON audit_export_snapshot (stream, "timestamp" DESC, id DESC);


/* =========================================================
   RETENTION DELETE OPTIMIZATION
   ========================================================= */

CREATE INDEX idx_retention_admin
    ON admin_audit_events (timestamp, id);

CREATE INDEX idx_retention_authentication
    ON authentication_events (timestamp, id);

CREATE INDEX idx_retention_credential_lifecycle
    ON credential_lifecycle_audit_events (timestamp, id);

CREATE INDEX idx_retention_identity_projection
    ON identity_projection_audit_events (timestamp, id);

CREATE INDEX idx_retention_lifecycle_denied
    ON lifecycle_denied_audit_events (timestamp, id);

CREATE INDEX idx_retention_rbac_denied
    ON rbac_denied_audit_events (timestamp, id);

CREATE INDEX idx_retention_onboarding
    ON onboarding_audit_events (timestamp, id);

CREATE INDEX idx_retention_sensitive_access
    ON sensitive_access_audit_events (timestamp, id);

CREATE INDEX idx_retention_unauth_access
    ON unauthenticated_access_audit_events (timestamp, id);
