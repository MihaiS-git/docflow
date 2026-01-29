package com.brutecx.docflow_backend.security.session;

import com.brutecx.docflow_backend.security.audit.AuditRequestContext;
import com.brutecx.docflow_backend.security.audit.lifecycle.ILifecycleDeniedAuditService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SessionRevocationService {

    private final SessionRegistry sessionRegistry;
    private final ILifecycleDeniedAuditService lifecycleDeniedAuditService;

    public SessionRevocationService(
            @Autowired(required = false) SessionRegistry sessionRegistry,
            ILifecycleDeniedAuditService lifecycleDeniedAuditService
    ) {
        this.sessionRegistry = sessionRegistry;
        this.lifecycleDeniedAuditService = lifecycleDeniedAuditService;
    }

    /**
     * Invalidates all active sessions for a given OIDC subject.
     */
    public int revokeSessionsBySubject(
            AuditRequestContext ctx,
            String targetExternalSubjectId,
            String actorExternalSubjectId
    ) {
        if (sessionRegistry == null) {
            return 0;
        }

        int revoked = 0;

        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!(principal instanceof OidcUser oidcUser)) {
                continue;
            }

            if (!targetExternalSubjectId.equals(oidcUser.getSubject())) {
                continue;
            }

            if (targetExternalSubjectId.equals(actorExternalSubjectId)) {
                continue;
            }

            List<SessionInformation> sessions =
                    sessionRegistry.getAllSessions(principal, false);

            for (SessionInformation session : sessions) {
                session.expireNow();
                revoked++;
            }
        }

        if (revoked > 0) {
            lifecycleDeniedAuditService.record(
                    ctx.requestId(),                 // same correlation model already used
                    targetExternalSubjectId,
                    "USER_SESSION_REVOKED",
                    "ADMIN_ACTION",
                    "SESSION_INVALIDATION",
                    ctx.ip(), // no HttpServletRequest available here (service layer)
                    ctx.userAgent()  // no UA available here (service layer)
            );
        }

        return revoked;
    }
}
