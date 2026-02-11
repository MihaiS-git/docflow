package com.brutecx.docflow_backend.security.session;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.lifecycle.ILifecycleDeniedAuditService;
import lombok.extern.slf4j.Slf4j;
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
    private final AuditRequestContextExtractor contextExtractor;

    public SessionRevocationService(
            @Autowired(required = false) SessionRegistry sessionRegistry,
            ILifecycleDeniedAuditService lifecycleDeniedAuditService,
            AuditRequestContextExtractor contextExtractor
    ) {
        this.sessionRegistry = sessionRegistry;
        this.lifecycleDeniedAuditService = lifecycleDeniedAuditService;
        this.contextExtractor = contextExtractor;
    }

    public int revokeSessionsBySubject(
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

            // prevent self-revocation through this path
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

            AuditRequestContext ctx = contextExtractor.fromCurrentRequest();

            if (ctx.correlationId() == null || ctx.correlationId().isBlank()) {
                throw new IllegalStateException(
                        "Missing correlationId during session revocation"
                );
            }

            String fingerprint = EventFingerprint.of(List.of(
                    "USER_SESSION_REVOKED",
                    targetExternalSubjectId,
                    String.valueOf(revoked),
                    ctx.correlationId()
            ));

            lifecycleDeniedAuditService.record(
                    targetExternalSubjectId,
                    "USER_SESSION_REVOKED",
                    "ADMIN_ACTION",
                    "SESSION_INVALIDATION",
                    fingerprint
            );
        }

        return revoked;
    }
}
