package com.brutecx.docflow_backend.security.mfa;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces MFA/assurance for sensitive endpoints after authentication.
 * <p>
 * Constraint: no Keycloak config changes and no login flow changes.
 */
@Component
@RequiredArgsConstructor
public class MfaAssuranceEnforcementFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (requiresMfa(path)) {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)) {

                // OIDC-only, per constraint
                boolean hasMfa = OidcAssuranceClaims.hasMfa(authentication);

                if (!hasMfa) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        { "errorCode": "mfa_required" }
                    """);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresMfa(String path) {
        return path != null &&
                (path.startsWith("/api/admin/")
                        || path.startsWith("/api/review/"));
    }
}
