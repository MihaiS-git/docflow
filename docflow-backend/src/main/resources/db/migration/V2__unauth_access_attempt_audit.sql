CREATE TABLE unauthenticated_access_audit_events
(
    id                UUID PRIMARY KEY,
    request_id        VARCHAR(128),
    http_method       VARCHAR(16)  NOT NULL,
    path              VARCHAR(512) NOT NULL,
    ip                VARCHAR(128),
    user_agent        VARCHAR(512),
    timestamp         TIMESTAMPTZ  NOT NULL,
    event_fingerprint VARCHAR(64)  NOT NULL UNIQUE
);

CREATE INDEX idx_unauth_access_timestamp
    ON unauthenticated_access_audit_events (timestamp);

CREATE INDEX idx_unauth_access_request_id
    ON unauthenticated_access_audit_events (request_id);

CREATE INDEX idx_unauth_access_path
    ON unauthenticated_access_audit_events (path);
