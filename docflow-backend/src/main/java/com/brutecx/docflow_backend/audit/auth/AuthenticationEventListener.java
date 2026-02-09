package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.identity.IUserIdentityProjectionService;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
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
    private final ClientIpResolver clientIpResolver;

    public AuthenticationEventListener(
            AuthenticationEventRepository repository,
            HttpServletRequest request,
            IUserIdentityProjectionService identityProjectionService,
            ClientIpResolver clientIpResolver
    ) {
        this.repository = repository;
        this.request = request;
        this.identityProjectionService = identityProjectionService;
        this.clientIpResolver = clientIpResolver;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        // pass authentication so resolver can use authentication.details remoteAddress first
        persist(AuthenticationResult.SUCCESS, event.getAuthentication().getName(), event.getAuthentication());

        String subjectId = resolveSubjectId(event.getAuthentication());
        log.info("Triggering identity projection subjectId={}", subjectId);
        identityProjectionService.ensureProjected(subjectId);
    }

    private String resolveSubjectId(Authentication authentication) {
        if (authentication == null) {
            return "UNKNOWN";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getSubject();
        }
        return "UNKNOWN";
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        persist(AuthenticationResult.FAILURE, event.getAuthentication().getName(), event.getAuthentication());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        persist(AuthenticationResult.LOGOUT, event.getAuthentication().getName(), event.getAuthentication());
    }

    private void persist(AuthenticationResult result, String username, Authentication authentication) {
        String correlationId = MDC.get("correlationId");
        String mdcSource = MDC.get("correlationSource");

        String resolvedUsername =
                (username != null && !username.isBlank()) ? username : "UNKNOWN";

        String ip = clientIpResolver.resolve(authentication, request);

        String userAgent = request.getHeader("User-Agent") != null
                ? request.getHeader("User-Agent")
                : "N/A";

        Instant eventTime = Instant.now();

        CorrelationSource correlationSource =
                "GENERATED".equalsIgnoreCase(mdcSource)
                                ? CorrelationSource.GENERATED
                                : CorrelationSource.REQUEST_ID;

        AuditResult auditResult =
                switch (result) {
                        case SUCCESS -> AuditResult.SUCCESS;
                        case FAILURE -> AuditResult.FAILED;
                        case LOGOUT -> AuditResult.SUCCESS;
                    };

        String eventFingerprint = EventFingerprint.of(List.of(
                result.name(),
                AuthenticationEventSource.SPRING_SECURITY.name(),
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
                correlationSource,
                ExecutionContext.AUTH_FLOW,
                auditResult,
                eventFingerprint
        );

        try {
            repository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            // duplicate event → safe to ignore
            return;
        } catch (Exception ex) {
            log.error(
                    "AUTH AUDIT FAILURE. result={} username={} correlationId={}",
                    result, resolvedUsername, correlationId, ex
            );
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
