package com.brutecx.docflow_backend.security.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * A filter that enforces an absolute session timeout.
 * If the session has existed longer than the configured absolute timeout,
 * it will be invalidated, and the user will be logged out.
 * This filter excludes certain paths from the timeout check,
 * such as OAuth2 and login endpoints.
 */
@Component
@RequiredArgsConstructor
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final SessionSecurityProperties props;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (path == null) return false;

        return path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.startsWith("/login/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Duration absoluteTimeout = props.absoluteTimeout();
        if (absoluteTimeout != null && !absoluteTimeout.isZero() && !absoluteTimeout.isNegative()) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                long nowMillis = System.currentTimeMillis();
                long createdMillis = session.getCreationTime();
                long maxAgeMillis = absoluteTimeout.toMillis();

                if (maxAgeMillis > 0 && (nowMillis - createdMillis) >= maxAgeMillis) {
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
