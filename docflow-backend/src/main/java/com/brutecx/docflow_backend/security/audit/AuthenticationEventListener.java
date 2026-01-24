package com.brutecx.docflow_backend.security.audit;

import com.brutecx.docflow_backend.security.audit.identity.IUserIdentityProjectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Listener for authentication events to log and persist them.
 * Handles successful logins, failed login attempts, and logouts.
 * Persists events to the AuthenticationEventRepository and logs them for auditing.
 * Also ensures user identity projection upon successful authentication.
 */
@Component
public class AuthenticationEventListener {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final AuthenticationEventRepository repository;
    private final HttpServletRequest request;
    private final IUserIdentityProjectionService identityProjectionService;

    public AuthenticationEventListener(
            AuthenticationEventRepository repository,
            HttpServletRequest request,
            IUserIdentityProjectionService identityProjectionService
    ) {
        this.repository = repository;
        this.request = request;
        this.identityProjectionService = identityProjectionService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String subjectId = event.getAuthentication().getName();
        persist(AuthenticationResult.SUCCESS, event.getAuthentication().getName());
        identityProjectionService.ensureProjected(subjectId);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        persist(AuthenticationResult.FAILURE, event.getAuthentication().getName());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        persist(AuthenticationResult.LOGOUT, event.getAuthentication().getName());
    }

    private void persist(AuthenticationResult result, String username) {
        String correlationId = MDC.get("requestId");

        String resolvedUsername =
                (username != null && !username.isBlank()) ? username : "UNKNOWN";

        String ip = request.getRemoteAddr() != null
                ? request.getRemoteAddr()
                : "UNKNOWN";

        String userAgent = request.getHeader("User-Agent") != null
                ? request.getHeader("User-Agent")
                : "N/A";

        Instant eventTime = Instant.now();

        String eventFingerprint = EventFingerprint.of(List.of(
                result.name(),
                AuthenticationEventSource.SPRING_SECURITY.name(),   // source
                resolvedUsername,
                ip,
                String.valueOf(eventTime.toEpochMilli())
        ));

        AuthenticationEvent entity = new AuthenticationEvent(
                AuthenticationEventSource.SPRING_SECURITY,
                eventTime,
                resolvedUsername,
                result,
                "KEYCLOAK",
                ip,
                userAgent,
                correlationId,
                eventFingerprint
        );

        try {
            repository.save(entity);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // duplicate event → safe to ignore
            return;
        }

        log.info(
                "auth_event result={} username={} idp={} ip={} ua={} correlationId={}",
                result,
                resolvedUsername,
                "KEYCLOAK",
                ip,
                userAgent,
                correlationId
        );
    }
}
