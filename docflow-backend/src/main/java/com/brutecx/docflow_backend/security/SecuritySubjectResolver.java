package com.brutecx.docflow_backend.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class SecuritySubjectResolver {

    private static final String UNKNOWN = "UNKNOWN";

    public String resolveSubjectId() {

        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof OidcUser oidcUser) {
            String subject = oidcUser.getSubject();

            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }

        return UNKNOWN;
    }
}