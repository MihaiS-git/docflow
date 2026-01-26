package com.brutecx.docflow_backend.security.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SessionRevocationService {

    private final SessionRegistry sessionRegistry;

    public SessionRevocationService(@Autowired(required = false) SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Invalidates all active sessions for a given OIDC subject.
     */
    public int revokeSessionsBySubject(String externalSubjectId) {
        if (sessionRegistry == null) {
            // Test / non-session context → nothing to revoke
            return 0;
        }

        int revoked = 0;

        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!(principal instanceof OidcUser oidcUser)) {
                continue;
            }

            if (!externalSubjectId.equals(oidcUser.getSubject())) {
                continue;
            }

            List<SessionInformation> sessions =
                    sessionRegistry.getAllSessions(principal, false);
            for (SessionInformation session : sessions) {
                session.expireNow();
                revoked++;
            }
        }

        return revoked;
    }
}
