package com.brutecx.docflow_backend.security.session.filter;

import com.brutecx.docflow_backend.logging.InfraEventActions;
import com.brutecx.docflow_backend.logging.InfraEventLogger;
import com.brutecx.docflow_backend.logging.InfraEventOutcome;
import com.brutecx.docflow_backend.logging.InfraEventType;
import com.brutecx.docflow_backend.security.session.SessionSecurityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Enforces absolute session timeout.
 * Emits:
 *  - Structured infra event on enforcement
 *  - Micrometer counter increment
 * Does NOT emit noise for normal session flow.
 */
@Component
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final SessionSecurityProperties props;
    private final Counter absoluteTimeoutCounter;

    public AbsoluteSessionTimeoutFilter(
            SessionSecurityProperties props,
            MeterRegistry meterRegistry
    ) {
        this.props = props;
        this.absoluteTimeoutCounter = Counter.builder("docflow.session.absolute.timeout")
                .description("Number of sessions invalidated due to absolute timeout")
                .register(meterRegistry);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }

        return path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.startsWith("/login/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Duration absoluteTimeout = props.absoluteTimeout();

        if (absoluteTimeout != null
                && !absoluteTimeout.isZero()
                && !absoluteTimeout.isNegative()) {

            HttpSession session = request.getSession(false);

            if (session != null) {

                long nowMillis = System.currentTimeMillis();
                long createdMillis = session.getCreationTime();
                long maxAgeMillis = absoluteTimeout.toMillis();

                if (maxAgeMillis > 0
                        && (nowMillis - createdMillis) >= maxAgeMillis) {

                    String subjectId = resolveSubjectId();

                    // Invalidate session
                    session.invalidate();
                    SecurityContextHolder.clearContext();

                    // Metrics
                    absoluteTimeoutCounter.increment();

                    // Structured Infra Log
                    InfraEventLogger.log(
                            InfraEventType.AUTHENTICATION,
                            InfraEventActions.AUTHN_SESSION_ABSOLUTE_TIMEOUT,
                            InfraEventOutcome.SUCCESS,
                            "Absolute session timeout enforced",
                            null,
                            net.logstash.logback.argument.StructuredArguments.kv("http.method", request.getMethod()),
                            net.logstash.logback.argument.StructuredArguments.kv("http.path", request.getRequestURI()),
                            net.logstash.logback.argument.StructuredArguments.kv("actor.subject_id", subjectId)
                    );

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveSubjectId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getSubject();
        }

        return null;
    }
}