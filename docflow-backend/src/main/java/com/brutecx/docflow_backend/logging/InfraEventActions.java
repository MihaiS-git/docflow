package com.brutecx.docflow_backend.logging;

public final class InfraEventActions {

    private InfraEventActions() {
    }

    /* FORMAT <SCOPE>_<VERB>_<OBJECT>[_<QUALIFIER>] */

    /* =====================================================
       AUTHENTICATION
       ===================================================== */

    public static final String AUTHN_LAST_LOGIN_UPDATE = "authn_last_login_update";
    public static final String AUTHN_UNAUTH_ACCESS_AUDIT_WRITE = "authn_unauth_access_audit_write";

    public static final String AUTHN_SESSION_ABSOLUTE_TIMEOUT = "authn_session_absolute_timeout";
    public static final String AUTHN_SESSION_REVOKE_ADMIN = "authn_session_revoke_admin";

    public static final String AUTHN_CORRELATION_ID_REJECTED = "authn_correlation_id_rejected";

    /* =====================================================
       AUTHORIZATION
       ===================================================== */

    public static final String AUTHZ_LIFECYCLE_DENIED_AUDIT_WRITE = "authz_lifecycle_denied_audit_write";
    public static final String AUTHZ_RBAC_DENIED_AUDIT_WRITE = "authz_rbac_denied_audit_write";

    /* =====================================================
       MAIL
       ===================================================== */

    public static final String MAIL_SMTP_SEND = "mail_smtp_send";

    /* =====================================================
       DATABASE
       ===================================================== */

    public static final String DB_ENTITY_PERSIST = "db_entity_persist";
    public static final String DB_QUERY_EXECUTE = "db_query_execute";
    public static final String DB_TRANSACTION_COMMIT = "db_transaction_commit";

    /* =====================================================
       STARTUP
       ===================================================== */

    public static final String STARTUP_SERVICE_START = "startup_service_start";
}