CREATE TABLE public.admin_audit_events
(
    id                 uuid                        NOT NULL,
    action_type        character varying(255)      NOT NULL,
    actor_user_id      uuid                        NOT NULL,
    chain_segment_hash character varying(128),
    chain_version      integer                     NOT NULL,
    correlation_id     character varying(128)      NOT NULL,
    correlation_source character varying(255)      NOT NULL,
    event_fingerprint  character varying(128)      NOT NULL,
    event_hash         character varying(128)      NOT NULL,
    execution_context  character varying(255)      NOT NULL,
    ip                 character varying(128)      NOT NULL,
    metadata           jsonb,
    prev_event_hash    character varying(128)      NOT NULL,
    result             character varying(255)      NOT NULL,
    subject_id         character varying(128)      NOT NULL,
    target_user_id     uuid,
    tenant_id          uuid                        NOT NULL,
    "timestamp"        timestamp(6) with time zone NOT NULL,
    user_agent         character varying(512)      NOT NULL,
    CONSTRAINT admin_audit_events_action_type_check CHECK (((action_type)::text = ANY ((ARRAY['ROLE_ASSIGNED':: character varying, 'ROLE_REVOKED':: character varying, 'ROLE_REVOKE_FAILED':: character varying, 'ROLE_ASSIGN_FAILED':: character varying, 'BOOTSTRAP_ACTIVATED':: character varying, 'USER_ACTIVATED':: character varying, 'USER_LOCKED':: character varying, 'USER_DISABLED':: character varying, 'USER_ACTIVATE_FAILED':: character varying, 'USER_DISABLE_FAILED':: character varying, 'USER_LOCK_FAILED':: character varying, 'USER_INVITED':: character varying, 'INVITE_FAILED':: character varying, 'INVITE_CLEANUP':: character varying, 'INVITE_REVOKED':: character varying, 'INVITE_SUBJECT_BOUND':: character varying, 'INVITE_SUBJECT_BIND_FAILED':: character varying, 'INVITE_PURGE_FAILED':: character varying, 'TENANT_CREATED':: character varying, 'TENANT_CREATE_FAILED':: character varying, 'TENANT_UPDATED':: character varying, 'TENANT_SUSPENDED':: character varying, 'TENANT_MUTATION_DENIED':: character varying, 'RETENTION_POLICY_UPSERT':: character varying, 'RETENTION_POLICY_UPSERT_FAILED':: character varying])::text[])
) ),
    CONSTRAINT admin_audit_events_correlation_source_check CHECK (((correlation_source)::text = ANY ((ARRAY['REQUEST_ID'::character varying, 'GENERATED'::character varying, 'ADMIN_EVENT_ID'::character varying, 'SESSION_ID'::character varying, 'PULL_RUN'::character varying])::text[]))),
    CONSTRAINT admin_audit_events_execution_context_check CHECK (((execution_context)::text = ANY ((ARRAY['HTTP'::character varying, 'SCHEDULED_JOB'::character varying, 'AUTH_FLOW'::character varying, 'ADMIN_API'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT admin_audit_events_result_check CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'DENIED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.audit_chain_checkpoints
(
    stream               character varying(255)      NOT NULL,
    last_event_timestamp timestamp(6) with time zone NOT NULL
);

CREATE TABLE public.audit_chain_state
(
    state_key               character varying(256)      NOT NULL,
    stream                  character varying(128)      NOT NULL,
    tenant_id               character varying(64),
    last_event_hash         character varying(128)      NOT NULL,
    last_checkpoint_hash    character varying(128),
    last_checkpoint_at      timestamp(6) with time zone,
    events_since_checkpoint integer                     NOT NULL,
    event_count             bigint                      NOT NULL,
    updated_at              timestamp(6) with time zone NOT NULL
);

CREATE TABLE public.audit_export_snapshot
(
    id                uuid                        NOT NULL,
    created_at        timestamp(6) with time zone NOT NULL,
    created_by        uuid                        NOT NULL,
    from_ts           timestamp(6) with time zone NOT NULL,
    key_id            character varying(128),
    row_count         bigint                      NOT NULL,
    sha256_digest_hex character varying(64)       NOT NULL,
    signature_alg     character varying(64)       NOT NULL,
    signature_b64     character varying(1024)     NOT NULL,
    stream            character varying(128)      NOT NULL,
    tenant_id         uuid,
    to_ts             timestamp(6) with time zone NOT NULL
);

CREATE TABLE public.audit_retention_policies
(
    id              uuid                        NOT NULL,
    archive_enabled boolean                     NOT NULL,
    created_at      timestamp(6) with time zone NOT NULL,
    retention_days  integer                     NOT NULL,
    stream_name     character varying(128)      NOT NULL,
    updated_at      timestamp(6) with time zone NOT NULL
);

CREATE TABLE public.audit_signing_keys
(
    key_id                 character varying(128)      NOT NULL,
    active                 boolean                     NOT NULL,
    created_at             timestamp(6) with time zone NOT NULL,
    encrypted_private_key  bytea                       NOT NULL,
    expires_at             timestamp(6) with time zone NOT NULL,
    fingerprint_sha256_hex character varying(64)       NOT NULL,
    public_key_pem         text                        NOT NULL
);

CREATE TABLE public.authentication_events
(
    id                    uuid                        NOT NULL,
    authentication_result character varying(32)       NOT NULL,
    chain_segment_hash    character varying(128),
    chain_version         integer                     NOT NULL,
    correlation_id        character varying(128),
    correlation_source    character varying(32)       NOT NULL,
    event_fingerprint     character varying(128)      NOT NULL,
    event_hash            character varying(128)      NOT NULL,
    execution_context     character varying(32)       NOT NULL,
    idp                   character varying(64)       NOT NULL,
    ip                    character varying(128)      NOT NULL,
    metadata              jsonb,
    prev_event_hash       character varying(128)      NOT NULL,
    audit_result          character varying(16)       NOT NULL,
    source                character varying(64)       NOT NULL,
    subject_id            character varying(128)      NOT NULL,
    "timestamp"           timestamp(6) with time zone NOT NULL,
    user_agent            character varying(512)      NOT NULL,
    username              character varying(128)      NOT NULL,
    CONSTRAINT authentication_events_audit_result_check CHECK (((audit_result)::text = ANY ((ARRAY['SUCCESS':: character varying, 'DENIED':: character varying, 'FAILED':: character varying])::text[])
) ),
    CONSTRAINT authentication_events_authentication_result_check CHECK (((authentication_result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying, 'LOGOUT'::character varying])::text[]))),
    CONSTRAINT authentication_events_correlation_source_check CHECK (((correlation_source)::text = ANY ((ARRAY['REQUEST_ID'::character varying, 'GENERATED'::character varying, 'ADMIN_EVENT_ID'::character varying, 'SESSION_ID'::character varying, 'PULL_RUN'::character varying])::text[]))),
    CONSTRAINT authentication_events_execution_context_check CHECK (((execution_context)::text = ANY ((ARRAY['HTTP'::character varying, 'SCHEDULED_JOB'::character varying, 'AUTH_FLOW'::character varying, 'ADMIN_API'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT authentication_events_source_check CHECK (((source)::text = ANY ((ARRAY['SPRING_SECURITY'::character varying, 'KEYCLOAK_ADMIN_EVENTS'::character varying])::text[])))
);

CREATE TABLE public.credential_lifecycle_audit_events
(
    id                  uuid                        NOT NULL,
    chain_segment_hash  character varying(128),
    chain_version       integer                     NOT NULL,
    client_id           character varying(128),
    correlation_id      character varying(128)      NOT NULL,
    correlation_source  character varying(32)       NOT NULL,
    event_fingerprint   character varying(128)      NOT NULL,
    event_hash          character varying(128)      NOT NULL,
    event_type          character varying(64)       NOT NULL,
    execution_context   character varying(32)       NOT NULL,
    ip                  character varying(128)      NOT NULL,
    prev_event_hash     character varying(128)      NOT NULL,
    reason_code         character varying(64)       NOT NULL,
    reason_detail       character varying(512),
    required_action     character varying(128),
    result              character varying(16)       NOT NULL,
    session_id          character varying(128),
    subject_external_id character varying(128),
    "timestamp"         timestamp(6) with time zone NOT NULL,
    CONSTRAINT credential_lifecycle_audit_events_correlation_source_check CHECK (((correlation_source)::text = ANY ((ARRAY['REQUEST_ID':: character varying, 'GENERATED':: character varying, 'ADMIN_EVENT_ID':: character varying, 'SESSION_ID':: character varying, 'PULL_RUN':: character varying])::text[])
) ),
    CONSTRAINT credential_lifecycle_audit_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['PASSWORD_CHANGED'::character varying, 'PASSWORD_RESET'::character varying, 'MFA_ENROLLED'::character varying, 'MFA_REMOVED'::character varying, 'REQUIRED_ACTION_SET'::character varying, 'REQUIRED_ACTION_CLEARED'::character varying, 'UNKNOWN'::character varying])::text[]))),
    CONSTRAINT credential_lifecycle_audit_events_execution_context_check CHECK (((execution_context)::text = ANY ((ARRAY['HTTP'::character varying, 'SCHEDULED_JOB'::character varying, 'AUTH_FLOW'::character varying, 'ADMIN_API'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT credential_lifecycle_audit_events_result_check CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'DENIED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.identity_projection_audit_events
(
    id                 uuid                        NOT NULL,
    chain_segment_hash character varying(128),
    chain_version      integer                     NOT NULL,
    correlation_id     character varying(128)      NOT NULL,
    correlation_source character varying(32)       NOT NULL,
    event_fingerprint  character varying(128)      NOT NULL,
    event_hash         character varying(128)      NOT NULL,
    execution_context  character varying(32)       NOT NULL,
    prev_event_hash    character varying(128)      NOT NULL,
    reason_code        character varying(64)       NOT NULL,
    result             character varying(16)       NOT NULL,
    subject_id         character varying(128)      NOT NULL,
    "timestamp"        timestamp(6) with time zone NOT NULL,
    CONSTRAINT identity_projection_audit_events_correlation_source_check CHECK (((correlation_source)::text = ANY ((ARRAY['REQUEST_ID':: character varying, 'GENERATED':: character varying, 'ADMIN_EVENT_ID':: character varying, 'SESSION_ID':: character varying, 'PULL_RUN':: character varying])::text[])
) ),
    CONSTRAINT identity_projection_audit_events_execution_context_check CHECK (((execution_context)::text = ANY ((ARRAY['HTTP'::character varying, 'SCHEDULED_JOB'::character varying, 'AUTH_FLOW'::character varying, 'ADMIN_API'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT identity_projection_audit_events_result_check CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'DENIED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.invites
(
    id          uuid                        NOT NULL,
    created_at  timestamp(6) with time zone NOT NULL,
    email       character varying(255)      NOT NULL,
    first_name  character varying(255)      NOT NULL,
    last_name   character varying(255)      NOT NULL,
    job_title   character varying(255),
    department  character varying(255),
    expires_at  timestamp(6) with time zone NOT NULL,
    status      character varying(255)      NOT NULL,
    tenant_id   uuid                        NOT NULL,
    tenant_role character varying(32),
    token       character varying(64)       NOT NULL,
    user_id     uuid,
    CONSTRAINT invites_status_check CHECK (
        status IN ('PENDING', 'ACCEPTED')
),

    CONSTRAINT invites_tenant_role_check CHECK (
        tenant_role IN ('MEMBER', 'EXECUTOR', 'REVIEWER', 'MANAGER')
        )
);

CREATE TABLE public.keycloak_event_checkpoint
(
    id                 character varying(255) NOT NULL,
    last_event_time_ms bigint                 NOT NULL
);

CREATE TABLE public.lifecycle_denied_audit_events
(
    id                 uuid                        NOT NULL,
    chain_segment_hash character varying(128),
    chain_version      integer                     NOT NULL,
    correlation_id     character varying(128)      NOT NULL,
    correlation_source character varying(32)       NOT NULL,
    event_fingerprint  character varying(128)      NOT NULL,
    event_hash         character varying(128)      NOT NULL,
    execution_context  character varying(32)       NOT NULL,
    http_method        character varying(16)       NOT NULL,
    ip                 character varying(128)      NOT NULL,
    path               character varying(512)      NOT NULL,
    prev_event_hash    character varying(128)      NOT NULL,
    reason_code        character varying(64)       NOT NULL,
    result             character varying(16)       NOT NULL,
    subject_id         character varying(128)      NOT NULL,
    "timestamp"        timestamp(6) with time zone NOT NULL,
    user_agent         character varying(512)      NOT NULL,
    CONSTRAINT lifecycle_denied_audit_events_correlation_source_check CHECK (((correlation_source)::text = ANY ((ARRAY['REQUEST_ID':: character varying, 'GENERATED':: character varying, 'ADMIN_EVENT_ID':: character varying, 'SESSION_ID':: character varying, 'PULL_RUN':: character varying])::text[])
) ),
    CONSTRAINT lifecycle_denied_audit_events_execution_context_check CHECK (((execution_context)::text = ANY ((ARRAY['HTTP'::character varying, 'SCHEDULED_JOB'::character varying, 'AUTH_FLOW'::character varying, 'ADMIN_API'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT lifecycle_denied_audit_events_result_check CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'DENIED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.onboarding_audit_events
(
    id                 uuid                        NOT NULL,
    actor_user_id      uuid,
    chain_segment_hash character varying(128),
    chain_version      integer                     NOT NULL,
    correlation_id     character varying(128)      NOT NULL,
    correlation_source character varying(32)       NOT NULL,
    event_fingerprint  character varying(128)      NOT NULL,
    event_hash         character varying(128)      NOT NULL,
    execution_context  character varying(32)       NOT NULL,
    invite_id          uuid                        NOT NULL,
    ip                 character varying(128)      NOT NULL,
    outcome            character varying(32)       NOT NULL,
    prev_event_hash    character varying(128)      NOT NULL,
    reason_code        character varying(64)       NOT NULL,
    reason_detail      character varying(512),
    result             character varying(16)       NOT NULL,
    subject_id         character varying(128)      NOT NULL,
    tenant_id          uuid                        NOT NULL,
    "timestamp"        timestamp(6) with time zone NOT NULL,
    user_agent         character varying(512)      NOT NULL,
    CONSTRAINT onboarding_audit_events_correlation_source_check CHECK (((correlation_source)::text = ANY ((ARRAY['REQUEST_ID':: character varying, 'GENERATED':: character varying, 'ADMIN_EVENT_ID':: character varying, 'SESSION_ID':: character varying, 'PULL_RUN':: character varying])::text[])
) ),
    CONSTRAINT onboarding_audit_events_execution_context_check CHECK (((execution_context)::text = ANY ((ARRAY['HTTP'::character varying, 'SCHEDULED_JOB'::character varying, 'AUTH_FLOW'::character varying, 'ADMIN_API'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT onboarding_audit_events_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying])::text[]))),
    CONSTRAINT onboarding_audit_events_result_check CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'DENIED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.rbac_denied_audit_events
(
    id                 uuid                        NOT NULL,
    chain_segment_hash character varying(128),
    chain_version      integer                     NOT NULL,
    correlation_id     character varying(128)      NOT NULL,
    correlation_source character varying(32)       NOT NULL,
    event_fingerprint  character varying(128)      NOT NULL,
    event_hash         character varying(128)      NOT NULL,
    execution_context  character varying(32)       NOT NULL,
    http_method        character varying(16)       NOT NULL,
    ip                 character varying(128)      NOT NULL,
    path               character varying(512)      NOT NULL,
    prev_event_hash    character varying(128)      NOT NULL,
    result             character varying(16)       NOT NULL,
    subject_id         character varying(128)      NOT NULL,
    "timestamp"        timestamp(6) with time zone NOT NULL,
    user_agent         character varying(512)      NOT NULL,
    CONSTRAINT rbac_denied_audit_events_correlation_source_check CHECK (((correlation_source)::text = ANY ((ARRAY['REQUEST_ID':: character varying, 'GENERATED':: character varying, 'ADMIN_EVENT_ID':: character varying, 'SESSION_ID':: character varying, 'PULL_RUN':: character varying])::text[])
) ),
    CONSTRAINT rbac_denied_audit_events_execution_context_check CHECK (((execution_context)::text = ANY ((ARRAY['HTTP'::character varying, 'SCHEDULED_JOB'::character varying, 'AUTH_FLOW'::character varying, 'ADMIN_API'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT rbac_denied_audit_events_result_check CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'DENIED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.sensitive_access_audit_events
(
    id                        uuid                        NOT NULL,
    action                    character varying(64)       NOT NULL,
    actor_external_subject_id character varying(128),
    actor_user_id             uuid,
    chain_segment_hash        character varying(128),
    chain_version             integer                     NOT NULL,
    correlation_id            character varying(128)      NOT NULL,
    correlation_source        character varying(32)       NOT NULL,
    data_classification       character varying(32)       NOT NULL,
    event_fingerprint         character varying(128)      NOT NULL,
    event_hash                character varying(128)      NOT NULL,
    execution_context         character varying(32)       NOT NULL,
    ip                        character varying(128)      NOT NULL,
    prev_event_hash           character varying(128)      NOT NULL,
    reason_code               character varying(64)       NOT NULL,
    reason_detail             character varying(512),
    resource                  character varying(128)      NOT NULL,
    resource_path             character varying(512),
    result                    character varying(16)       NOT NULL,
    subject_id                character varying(128)      NOT NULL,
    subject_type              character varying(64)       NOT NULL,
    tenant_id                 uuid,
    "timestamp"               timestamp(6) with time zone NOT NULL,
    user_agent                character varying(512)      NOT NULL,
    CONSTRAINT sensitive_access_audit_events_correlation_source_check CHECK (((correlation_source)::text = ANY ((ARRAY['REQUEST_ID':: character varying, 'GENERATED':: character varying, 'ADMIN_EVENT_ID':: character varying, 'SESSION_ID':: character varying, 'PULL_RUN':: character varying])::text[])
) ),
    CONSTRAINT sensitive_access_audit_events_data_classification_check CHECK (((data_classification)::text = ANY ((ARRAY['INTERNAL'::character varying, 'CONFIDENTIAL'::character varying, 'RESTRICTED'::character varying, 'REGULATED'::character varying])::text[]))),
    CONSTRAINT sensitive_access_audit_events_execution_context_check CHECK (((execution_context)::text = ANY ((ARRAY['HTTP'::character varying, 'SCHEDULED_JOB'::character varying, 'AUTH_FLOW'::character varying, 'ADMIN_API'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT sensitive_access_audit_events_result_check CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'DENIED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT sensitive_access_audit_events_subject_type_check CHECK (((subject_type)::text = ANY ((ARRAY['USER'::character varying, 'TENANT'::character varying, 'AUDIT_STREAM'::character varying, 'RBAC_TOPOLOGY'::character varying, 'SECURITY_CONFIGURATION'::character varying, 'SYSTEM'::character varying])::text[])))
);

CREATE TABLE public.tenants
(
    id                uuid                        NOT NULL,
    bootstrap_enabled boolean                     NOT NULL,
    created_at        timestamp(6) with time zone NOT NULL,
    data_region       character varying(512),
    name              character varying(128)      NOT NULL,
    retention_days    bigint,
    status            character varying(255)      NOT NULL,
    tenant_type       character varying(32)       NOT NULL,
    updated_at        timestamp(6) with time zone NOT NULL,
    parent_tenant_id  uuid,
    CONSTRAINT tenants_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE':: character varying, 'SUSPENDED':: character varying])::text[])
) ),
    CONSTRAINT tenants_tenant_type_check CHECK (((tenant_type)::text = ANY ((ARRAY['ROOT'::character varying, 'DEPARTMENT'::character varying])::text[])))
);

CREATE TABLE public.unauthenticated_access_audit_events
(
    id                 uuid                        NOT NULL,
    chain_segment_hash character varying(128),
    chain_version      integer                     NOT NULL,
    correlation_id     character varying(128),
    correlation_source character varying(32)       NOT NULL,
    event_fingerprint  character varying(128)      NOT NULL,
    event_hash         character varying(128)      NOT NULL,
    execution_context  character varying(32)       NOT NULL,
    http_method        character varying(16)       NOT NULL,
    ip                 character varying(128),
    path               character varying(512)      NOT NULL,
    prev_event_hash    character varying(128)      NOT NULL,
    result             character varying(16)       NOT NULL,
    "timestamp"        timestamp(6) with time zone NOT NULL,
    user_agent         character varying(512),
    CONSTRAINT unauthenticated_access_audit_events_correlation_source_check CHECK (((correlation_source)::text = ANY ((ARRAY['REQUEST_ID':: character varying, 'GENERATED':: character varying, 'ADMIN_EVENT_ID':: character varying, 'SESSION_ID':: character varying, 'PULL_RUN':: character varying])::text[])
) ),
    CONSTRAINT unauthenticated_access_audit_events_execution_context_check CHECK (((execution_context)::text = ANY ((ARRAY['HTTP'::character varying, 'SCHEDULED_JOB'::character varying, 'AUTH_FLOW'::character varying, 'ADMIN_API'::character varying, 'SYSTEM'::character varying])::text[]))),
    CONSTRAINT unauthenticated_access_audit_events_result_check CHECK (((result)::text = ANY ((ARRAY['SUCCESS'::character varying, 'DENIED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.user_identity_projection
(
    subject_id     character varying(255)      NOT NULL,
    display_name   character varying(255),
    email          character varying(255),
    last_synced_at timestamp(6) with time zone NOT NULL,
    source         character varying(255)      NOT NULL,
    username       character varying(255)
);

CREATE TABLE public.user_tenant_memberships
(
    id         uuid                        NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    role       character varying(32)       NOT NULL,
    status     character varying(32)       NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    tenant_id  uuid                        NOT NULL,
    user_id    uuid                        NOT NULL,
    CONSTRAINT user_tenant_memberships_role_check CHECK (((role)::text = ANY ((ARRAY['MEMBER':: character varying, 'EXECUTOR':: character varying, 'REVIEWER':: character varying, 'MANAGER':: character varying])::text[])
) ),
    CONSTRAINT user_tenant_memberships_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])))
);

CREATE TABLE public.users
(
    id                    uuid                        NOT NULL,
    business_phone        character varying(255),
    created_at            timestamp(6) with time zone NOT NULL,
    department            character varying(255),
    display_name          character varying(255)      NOT NULL,
    email                 character varying(320)      NOT NULL,
    external_subject_id   character varying(128),
    first_name            character varying(255)      NOT NULL,
    job_title             character varying(255),
    last_login_at         timestamp(6) with time zone,
    last_login_ip         character varying(255),
    last_login_user_agent character varying(255),
    last_name             character varying(255)      NOT NULL,
    status                character varying(255)      NOT NULL,
    updated_at            timestamp(6) with time zone NOT NULL,
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE':: character varying, 'LOCKED':: character varying, 'DISABLED':: character varying])::text[])
) )
);

ALTER TABLE ONLY public.admin_audit_events
    ADD CONSTRAINT admin_audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.audit_chain_checkpoints
    ADD CONSTRAINT audit_chain_checkpoints_pkey PRIMARY KEY (stream);

ALTER TABLE ONLY public.audit_chain_state
    ADD CONSTRAINT audit_chain_state_pkey PRIMARY KEY (state_key);

ALTER TABLE ONLY public.audit_export_snapshot
    ADD CONSTRAINT audit_export_snapshot_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.audit_retention_policies
    ADD CONSTRAINT audit_retention_policies_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.audit_signing_keys
    ADD CONSTRAINT audit_signing_keys_pkey PRIMARY KEY (key_id);

ALTER TABLE ONLY public.authentication_events
    ADD CONSTRAINT authentication_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.credential_lifecycle_audit_events
    ADD CONSTRAINT credential_lifecycle_audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.identity_projection_audit_events
    ADD CONSTRAINT identity_projection_audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.invites
    ADD CONSTRAINT invites_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.keycloak_event_checkpoint
    ADD CONSTRAINT keycloak_event_checkpoint_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.lifecycle_denied_audit_events
    ADD CONSTRAINT lifecycle_denied_audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.onboarding_audit_events
    ADD CONSTRAINT onboarding_audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.rbac_denied_audit_events
    ADD CONSTRAINT rbac_denied_audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.sensitive_access_audit_events
    ADD CONSTRAINT sensitive_access_audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.identity_projection_audit_events
    ADD CONSTRAINT uk1sd8jmatvf8c3w0ui72ptavpx UNIQUE (event_fingerprint);

ALTER TABLE ONLY public.invites
    ADD CONSTRAINT uk1ws9kt1ybdrcww2o5w8300lty UNIQUE (token);

ALTER TABLE ONLY public.invites
    ADD CONSTRAINT fk_invites_tenant
    FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.lifecycle_denied_audit_events
    ADD CONSTRAINT uk2302wgydn44eg486dbwow14l7 UNIQUE (event_fingerprint);

ALTER TABLE ONLY public.rbac_denied_audit_events
    ADD CONSTRAINT uk3ajtlksiiex7a2n47ro2dtga4 UNIQUE (event_fingerprint);

ALTER TABLE ONLY public.admin_audit_events
    ADD CONSTRAINT uk8wen6x4k1mausrwjo9pi0sa4o UNIQUE (event_fingerprint);

ALTER TABLE ONLY public.audit_retention_policies
    ADD CONSTRAINT uk_audit_retention_policy_stream UNIQUE (stream_name);

ALTER TABLE ONLY public.audit_signing_keys
    ADD CONSTRAINT uk_audit_signing_keys_fingerprint UNIQUE (fingerprint_sha256_hex);

ALTER TABLE ONLY public.user_tenant_memberships
    ADD CONSTRAINT uk_membership_user_tenant UNIQUE (user_id, tenant_id);

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT uk_tenants_name UNIQUE (name);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_email UNIQUE (email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_external_subject UNIQUE (external_subject_id);

ALTER TABLE ONLY public.authentication_events
    ADD CONSTRAINT ukh62oq2ap0tj7d5bsmi9dkqoiq UNIQUE (event_fingerprint);

ALTER TABLE ONLY public.credential_lifecycle_audit_events
    ADD CONSTRAINT ukjas8p9xt1nb0ug0fiavm43v2f UNIQUE (event_fingerprint);

ALTER TABLE ONLY public.sensitive_access_audit_events
    ADD CONSTRAINT ukomjh70o1lq6dtcl2j94l5fmyw UNIQUE (event_fingerprint);

ALTER TABLE ONLY public.onboarding_audit_events
    ADD CONSTRAINT uksb37c88sf01tl2isgfy7oliwj UNIQUE (event_fingerprint);

ALTER TABLE ONLY public.unauthenticated_access_audit_events
    ADD CONSTRAINT unauthenticated_access_audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_identity_projection
    ADD CONSTRAINT user_identity_projection_pkey PRIMARY KEY (subject_id);

ALTER TABLE ONLY public.user_tenant_memberships
    ADD CONSTRAINT user_tenant_memberships_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

CREATE INDEX idx_audit_export_snapshot_created_at ON public.audit_export_snapshot USING btree (created_at);

CREATE INDEX idx_audit_export_snapshot_created_by ON public.audit_export_snapshot USING btree (created_by);

CREATE INDEX idx_audit_export_snapshot_stream ON public.audit_export_snapshot USING btree (stream);

CREATE INDEX idx_audit_export_snapshot_tenant ON public.audit_export_snapshot USING btree (tenant_id);

CREATE INDEX idx_audit_retention_policy_stream ON public.audit_retention_policies USING btree (stream_name);

CREATE INDEX idx_audit_signing_keys_expires_at ON public.audit_signing_keys USING btree (expires_at);

CREATE INDEX idx_invite_tenant_created_at ON public.invites USING btree (tenant_id, created_at);

CREATE INDEX idx_invite_tenant_email ON public.invites USING btree (tenant_id, email);

CREATE INDEX idx_invite_tenant_status_created ON public.invites USING btree (tenant_id, status, created_at);

CREATE INDEX idx_invite_tenant_status_expires ON public.invites USING btree (tenant_id, status, expires_at);

CREATE INDEX idx_membership_tenant_id ON public.user_tenant_memberships USING btree (tenant_id);

CREATE INDEX idx_membership_tenant_role_status ON public.user_tenant_memberships USING btree (tenant_id, role, status);

CREATE INDEX idx_membership_user_id ON public.user_tenant_memberships USING btree (user_id);

ALTER TABLE ONLY public.invites
    ADD CONSTRAINT fk4xplc9cq26hqi7g8u7ajv220v FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.user_tenant_memberships
    ADD CONSTRAINT fkjp5hc826j2rn3d625pjae8cl6 FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT fkk0041kqenpw17118b3xh5qfuc FOREIGN KEY (parent_tenant_id) REFERENCES public.tenants(id);

ALTER TABLE ONLY public.user_tenant_memberships
    ADD CONSTRAINT fklgl91rltwalvbj7icab2ge9y1 FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);
