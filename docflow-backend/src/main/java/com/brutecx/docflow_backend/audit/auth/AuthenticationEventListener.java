package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.identity.IUserIdentityProjectionService;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthenticationEventListener {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = "AUTH";

    private final AuthenticationEventRepository repository;
    private final IUserIdentityProjectionService identityProjectionService;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        persist(AuthenticationResult.SUCCESS, event.getAuthentication());
        identityProjectionService.ensureProjected(resolveSubjectId(event.getAuthentication()));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        persist(AuthenticationResult.FAILURE, event.getAuthentication());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        persist(AuthenticationResult.LOGOUT, event.getAuthentication());
    }

    private void persist(AuthenticationResult result, Authentication authentication) {

        ensureHttpContext();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String subjectId = resolveSubjectId(authentication);
        String username =
                (authentication != null && authentication.getName() != null && !authentication.getName().isBlank())
                        ? authentication.getName()
                        : "UNKNOWN";

        Instant eventTime = Instant.now();

        CorrelationSource correlationSource =
                "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                        ? CorrelationSource.GENERATED
                        : CorrelationSource.REQUEST_ID;

        AuditResult auditResult =
                (result == AuthenticationResult.FAILURE)
                        ? AuditResult.FAILED
                        : AuditResult.SUCCESS;

        List<String> fp = new ArrayList<>();
        fp.add(STREAM);
        fp.add(result.name());
        fp.add(username);
        fp.add(subjectId);
        fp.add(ctx.ip());
        fp.add(String.valueOf(eventTime.toEpochMilli()));
        fp.add(correlationId);

        String fingerprint = EventFingerprint.of(fp);

        String partitionKey =
                !"UNKNOWN".equals(subjectId)
                        ? subjectId
                        : STREAM + "_GLOBAL";

        String material = String.join("|",
                STREAM,
                result.name(),
                username,
                subjectId,
                ctx.ip(),
                correlationId,
                fingerprint
        );

        AuthenticationEvent entity = buildEntity(
                result,
                username,
                eventTime,
                ctx,
                correlationId,
                correlationSource,
                auditResult,
                fingerprint,
                partitionKey,
                material
        );

        try {
            repository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            log.debug(
                    "AUTH AUDIT DEDUPLICATED result={} username={} correlationId={}",
                    result,
                    username,
                    correlationId
            );
        } catch (Exception ex) {
            log.error(
                    "AUTH AUDIT FAILURE result={} username={} correlationId={}",
                    result,
                    username,
                    correlationId,
                    ex
            );
            throw ex;
        }
    }

    private AuthenticationEvent buildEntity(
            AuthenticationResult result,
            String username,
            Instant eventTime,
            AuditRequestContext ctx,
            String correlationId,
            CorrelationSource correlationSource,
            AuditResult auditResult,
            String fingerprint,
            String partitionKey,
            String material
    ) {
        AuditChainService.ChainHash chain =
                auditChainService.nextHash(
                        STREAM,
                        partitionKey,
                        material
                );

        return new AuthenticationEvent(
                AuthenticationEventSource.SPRING_SECURITY,
                eventTime,
                username,
                result,
                "KEYCLOAK",
                ctx.ip(),
                ctx.userAgent(),
                correlationId,
                correlationSource,
                ExecutionContext.AUTH_FLOW,
                auditResult,
                fingerprint,
                chain.chainVersion(),
                chain.prevHash(),
                chain.eventHash()
        );
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("Authentication audit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId for AUTH audit event");
        }
        return corr;
    }

    private String resolveSubjectId(Authentication authentication) {
        if (authentication == null) return "UNKNOWN";
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) return oidcUser.getSubject();
        return "UNKNOWN";
    }
}
