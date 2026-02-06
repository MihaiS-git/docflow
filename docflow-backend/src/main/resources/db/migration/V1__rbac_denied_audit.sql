CREATE TABLE rbac_denied_audit_events
(
    id                UUID PRIMARY KEY,
    request_id        VARCHAR(128),
    subject_id        VARCHAR(128),
    http_method       VARCHAR(16)  NOT NULL,
    path              VARCHAR(512) NOT NULL,
    ip                VARCHAR(128),
    user_agent        VARCHAR(512),
    timestamp         TIMESTAMPTZ  NOT NULL,
    event_fingerprint VARCHAR(64)  NOT NULL UNIQUE
);

CREATE INDEX idx_rbac_denied_timestamp
    ON rbac_denied_audit_events (timestamp);

CREATE INDEX idx_rbac_denied_subject_id
    ON rbac_denied_audit_events (subject_id);

CREATE INDEX idx_rbac_denied_request_id
    ON rbac_denied_audit_events (request_id);
