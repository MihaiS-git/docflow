package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.AuditStreamExecutor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.identity.IUserIdentityProjectionService;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.brutecx.docflow_backend.logging.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
@RequiredArgsConstructor
public class AuthenticationEventListener {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = AuthenticationAuditCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.AUTH_FLOW.name();

    private final AuthenticationEventRepository repository;
    private final IUserIdentityProjectionService identityProjectionService;
    private final AuditRequestContextExtractor contextExtractor;
    private final AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditWriteFailureMetrics metrics;
    private final AuditPartitionResolver partitionResolver;
    private final AuditStreamExecutor executor;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        persist(AuthenticationResult.SUCCESS, event.getAuthentication(), null, null);
        identityProjectionService.ensureProjected(resolveSubjectId(event.getAuthentication()));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        persistFailure(event);
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        persist(AuthenticationResult.LOGOUT, event.getAuthentication(), null, null);
    }

    private void persist(
            AuthenticationResult result,
            Authentication authentication,
            AuthenticationFailureReason failureReason,
            String failureDetail
    ) {
        ensureHttpContext();

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        final long startNs = System.nanoTime();

        String correlationId = requireCorrelation(ctx);
        if (correlationId == null) {
            Exception ex = new IllegalStateException("Missing correlationId for AUTH audit event");

            metrics.incrementFailure(STREAM, EXEC_CTX, ex);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

            log.error(
                    "security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "auth_audit_skipped_missing_correlation"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", STREAM),
                    kv("audit.partition", "SUBJECT"),
                    kv("correlation.missing", true),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );
            return;
        }

        String subjectId = resolveSubjectId(authentication);
        String username =
                (authentication != null && authentication.getName() != null && !authentication.getName().isBlank())
                        ? authentication.getName()
                        : "UNKNOWN";

        Instant eventTime = Instant.now();

        CorrelationSource correlationSource = ctx.correlationId() != null
                ? CorrelationSource.REQUEST_ID
                : CorrelationSource.GENERATED;

        AuditResult auditResult = (result == AuthenticationResult.FAILURE)
                ? AuditResult.FAILED
                : AuditResult.SUCCESS;

        AuthenticationAuditMetadata metadata;
        if (result == AuthenticationResult.FAILURE) {
            metadata = new AuthenticationFailureMetadata(failureReason, failureDetail);
        } else if (result == AuthenticationResult.LOGOUT) {
            metadata = new LogoutMetadata();
        } else {
            metadata = new AuthenticationSuccessMetadata("KEYCLOAK");
        }

        String fingerprint = EventFingerprint.of(List.of(
                STREAM,
                result.name(),
                username,
                subjectId,
                ctx.ip(),
                correlationId
        ));

        AuthenticationAuditCanonicalMaterialBuilder.Input canonicalInput =
                new AuthenticationAuditCanonicalMaterialBuilder.Input(
                        eventTime,
                        AuthenticationEventSource.SPRING_SECURITY,
                        username,
                        subjectId,
                        result,
                        metadata,
                        "KEYCLOAK",
                        ctx.ip(),
                        ctx.userAgent(),
                        correlationId,
                        correlationSource.name(),
                        EXEC_CTX,
                        auditResult.name(),
                        fingerprint
                );

        String canonicalMaterial =
                canonicalMaterialBuilder.buildCanonicalMaterial(canonicalInput);

        AuditPartition partition = partitionResolver.authentication(subjectId);

        try {
            AuditStreamExecutor.WriteOutcome outcome = executor.execute(
                    STREAM,
                    EXEC_CTX,
                    partition,
                    canonicalMaterial,
                    repository,
                    prepared -> new AuthenticationEvent(
                            canonicalInput.source(),
                            canonicalInput.timestamp(),
                            canonicalInput.username(),
                            canonicalInput.subjectId(),
                            canonicalInput.result(),
                            canonicalInput.idp(),
                            canonicalInput.ip(),
                            canonicalInput.userAgent(),
                            canonicalInput.correlationId(),
                            CorrelationSource.valueOf(canonicalInput.correlationSource()),
                            ExecutionContext.valueOf(canonicalInput.executionContext()),
                            AuditResult.valueOf(canonicalInput.auditResult()),
                            metadata,
                            canonicalInput.fingerprint(),
                            prepared.chainVersion(),
                            prepared.prevHash(),
                            prepared.eventHash()
                    )
            );

            if (outcome == AuditStreamExecutor.WriteOutcome.DEDUP) {
                log.debug(
                        "security_event",
                        kv("event.category", "audit"),
                        kv("event.action", "auth_audit_deduplicated"),
                        kv("audit.stream", STREAM),
                        kv("subject.id", subjectId),
                        kv("username", username),
                        kv("correlation.id", correlationId)
                );
            }
        } catch (Exception ex) {
            SecurityAuditLogger.auditFailure(
                    "auth_audit_record_failed",
                    STREAM,
                    "SUBJECT",
                    null,
                    ex,
                    correlationId
            );
        }
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("Authentication audit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            return null;
        }
        return corr;
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

    private void persistFailure(AbstractAuthenticationFailureEvent event) {
        AuthenticationFailureReason reason = mapReason(event.getException());
        String detail = event.getException().getClass().getSimpleName();

        persist(AuthenticationResult.FAILURE, event.getAuthentication(), reason, detail);
    }

    private AuthenticationFailureReason mapReason(Exception ex) {
        String name = ex.getClass().getSimpleName();

        return switch (name) {
            case "BadCredentialsException" -> AuthenticationFailureReason.INVALID_CREDENTIALS;
            case "UsernameNotFoundException" -> AuthenticationFailureReason.USER_NOT_FOUND;
            case "LockedException" -> AuthenticationFailureReason.ACCOUNT_LOCKED;
            case "DisabledException" -> AuthenticationFailureReason.ACCOUNT_DISABLED;
            case "CredentialsExpiredException" -> AuthenticationFailureReason.PASSWORD_EXPIRED;
            default -> AuthenticationFailureReason.UNKNOWN;
        };
    }
}