package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.identity.IUserIdentityProjectionService;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditWriteFailureMetrics metrics;
    private final ObjectMapper objectMapper;
    private final AuditPartitionResolver partitionResolver;

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

            log.error("security_event",
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

        CorrelationSource correlationSource =
                "GENERATED".equalsIgnoreCase(MDC.get(RequestCorrelationIdFilter.MDC_SOURCE_KEY))
                        ? CorrelationSource.GENERATED
                        : CorrelationSource.REQUEST_ID;

        AuditResult auditResult =
                (result == AuthenticationResult.FAILURE)
                        ? AuditResult.FAILED
                        : AuditResult.SUCCESS;

        AuthenticationAuditMetadata metadata;

        if (result == AuthenticationResult.FAILURE) {
            metadata = new AuthenticationFailureMetadata(
                    failureReason,
                    failureDetail
            );
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
                String.valueOf(eventTime.toEpochMilli()),
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
            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(partition, canonicalMaterial);

            repository.save(new AuthenticationEvent(
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
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));

            metrics.incrementSuccess(STREAM, EXEC_CTX);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

        } catch (DataIntegrityViolationException ex) {
            metrics.incrementDedup(STREAM, EXEC_CTX);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

        } catch (Exception ex) {
            metrics.incrementFailure(STREAM, EXEC_CTX, ex);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

            log.error("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "auth_audit_record_failed"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", STREAM),
                    kv("audit.partition", "SUBJECT"),
                    kv("correlation.id", correlationId),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );
        }
    }

    private String toDeterministicJson(AuthenticationAuditMetadata metadata) {
        if (metadata == null) return null;
        try {
            ObjectMapper m = objectMapper.copy()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return m.writeValueAsString(metadata);
        } catch (Exception ex) {
            // fail-closed: if metadata cannot be serialized deterministically, don't write audit event
            throw new IllegalStateException("Failed to serialize AUTH metadata", ex);
        }
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("Authentication audit invoked outside HTTP request context");
        }
    }

    /**
     * IMPORTANT: Do NOT throw here. Authentication events are part of the control-plane.
     * If correlation is missing, we fail safely by skipping persistence and recording failure telemetry.
     */
    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            return null;
        }
        return corr;
    }

    private String resolveSubjectId(Authentication authentication) {
        if (authentication == null) return "UNKNOWN";
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) return oidcUser.getSubject();
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