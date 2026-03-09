package com.brutecx.docflow_backend.audit.onboarding;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.AuditStreamExecutor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.brutecx.docflow_backend.logging.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class OnboardingAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = OnboardingCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();
    private static final String UNKNOWN_SUBJECT = "UNKNOWN";

    private final OnboardingAuditEventRepository repository;
    private final AuditRequestContextExtractor contextExtractor;
    private final OnboardingCanonicalMaterialBuilder canonicalBuilder;
    private final AuditPartitionResolver partitionResolver;
    private final AuditStreamExecutor executor;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            UUID inviteId
    ) {
        ensureHttpContext();
        requireNonNull(inviteId, "inviteId");
        requireNonNull(tenantId, "tenantId");

        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalStateException("Onboarding SUCCESS requires subjectId");
        }

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);
        Instant eventTimestamp = Instant.now();

        String fingerprint = EventFingerprint.of(List.of(
                STREAM,
                "SUCCESS",
                inviteId.toString(),
                subjectId,
                tenantId.toString(),
                correlationId
        ));

        CorrelationSource correlationSource = resolveCorrelationSource(ctx);

        OnboardingCanonicalMaterialBuilder.Input input =
                new OnboardingCanonicalMaterialBuilder.Input(
                        eventTimestamp,
                        actorUserId,
                        subjectId,
                        tenantId,
                        inviteId,
                        correlationId,
                        correlationSource.name(),
                        EXEC_CTX,
                        ctx.ip(),
                        ctx.userAgent(),
                        AuditResult.SUCCESS.name(),
                        OnboardingOutcome.SUCCESS,
                        "ONBOARDING_SUCCESS",
                        null,
                        fingerprint
                );

        String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(input);
        AuditPartition partition = partitionResolver.onboarding(tenantId.toString());

        try {
            AuditStreamExecutor.WriteOutcome outcome =
                    executor.execute(
                            STREAM,
                            EXEC_CTX,
                            partition,
                            canonicalMaterial,
                            repository,
                            prepared -> new OnboardingAuditEvent(
                                    eventTimestamp,
                                    actorUserId,
                                    subjectId,
                                    tenantId,
                                    inviteId,
                                    correlationId,
                                    correlationSource,
                                    ExecutionContext.HTTP,
                                    ctx.ip(),
                                    ctx.userAgent(),
                                    AuditResult.SUCCESS,
                                    OnboardingOutcome.SUCCESS,
                                    "ONBOARDING_SUCCESS",
                                    null,
                                    fingerprint,
                                    prepared.chainVersion(),
                                    prepared.prevHash(),
                                    prepared.eventHash()
                            )
                    );

            if (outcome == AuditStreamExecutor.WriteOutcome.DEDUP) {
                log.debug(
                        "security_event",
                        kv("event.category", "audit"),
                        kv("event.action", "onboarding_audit_deduplicated"),
                        kv("audit.stream", STREAM),
                        kv("correlation.id", correlationId),
                        kv("tenant.id", tenantId),
                        kv("invite.id", inviteId),
                        kv("subject.id", subjectId)
                );
            }
        } catch (Exception ex) {
            SecurityAuditLogger.auditFailure(
                    "onboarding_audit_record_failed",
                    STREAM,
                    "TENANT",
                    tenantId,
                    ex,
                    correlationId
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            UUID actorUserId,
            UUID tenantId,
            UUID inviteId,
            String failureReason
    ) {
        ensureHttpContext();
        requireNonNull(inviteId, "inviteId");
        requireNonNull(tenantId, "tenantId");

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);
        Instant eventTimestamp = Instant.now();

        String resolvedReason =
                (failureReason != null && !failureReason.isBlank())
                        ? failureReason
                        : "-";

        String fingerprint = EventFingerprint.of(List.of(
                STREAM,
                "FAILURE",
                inviteId.toString(),
                tenantId.toString(),
                resolvedReason,
                correlationId
        ));

        CorrelationSource correlationSource = resolveCorrelationSource(ctx);

        OnboardingCanonicalMaterialBuilder.Input input =
                new OnboardingCanonicalMaterialBuilder.Input(
                        eventTimestamp,
                        actorUserId,
                        null,
                        tenantId,
                        inviteId,
                        correlationId,
                        correlationSource.name(),
                        EXEC_CTX,
                        ctx.ip(),
                        ctx.userAgent(),
                        AuditResult.FAILED.name(),
                        OnboardingOutcome.FAILURE,
                        "ONBOARDING_FAILURE",
                        resolvedReason,
                        fingerprint
                );

        String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(input);
        AuditPartition partition = partitionResolver.onboarding(tenantId.toString());

        try {
            AuditStreamExecutor.WriteOutcome outcome =
                    executor.execute(
                            STREAM,
                            EXEC_CTX,
                            partition,
                            canonicalMaterial,
                            repository,
                            prepared -> new OnboardingAuditEvent(
                                    eventTimestamp,
                                    actorUserId,
                                    UNKNOWN_SUBJECT,
                                    tenantId,
                                    inviteId,
                                    correlationId,
                                    correlationSource,
                                    ExecutionContext.HTTP,
                                    ctx.ip(),
                                    ctx.userAgent(),
                                    AuditResult.FAILED,
                                    OnboardingOutcome.FAILURE,
                                    "ONBOARDING_FAILURE",
                                    resolvedReason,
                                    fingerprint,
                                    prepared.chainVersion(),
                                    prepared.prevHash(),
                                    prepared.eventHash()
                            )
                    );

            if (outcome == AuditStreamExecutor.WriteOutcome.DEDUP) {
                log.debug(
                        "security_event",
                        kv("event.category", "audit"),
                        kv("event.action", "onboarding_audit_deduplicated"),
                        kv("audit.stream", STREAM),
                        kv("correlation.id", correlationId),
                        kv("tenant.id", tenantId),
                        kv("invite.id", inviteId),
                        kv("onboarding.outcome", "FAILURE")
                );
            }
        } catch (Exception ex) {
            SecurityAuditLogger.auditFailure(
                    "onboarding_audit_record_failed",
                    STREAM,
                    "TENANT",
                    tenantId,
                    ex,
                    correlationId
            );
        }
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("OnboardingAudit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId for Onboarding audit");
        }
        return corr;
    }

    private static CorrelationSource resolveCorrelationSource(AuditRequestContext ctx) {
        return ctx.correlationId() != null
                ? CorrelationSource.REQUEST_ID
                : CorrelationSource.GENERATED;
    }

    private static void requireNonNull(Object v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}