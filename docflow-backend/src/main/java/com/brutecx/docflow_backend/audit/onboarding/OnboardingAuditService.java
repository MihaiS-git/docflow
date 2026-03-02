package com.brutecx.docflow_backend.audit.onboarding;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
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

    private final OnboardingAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final OnboardingCanonicalMaterialBuilder canonicalBuilder;
    private final AuditWriteFailureMetrics metrics;
    private final AuditPartitionResolver partitionResolver;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            UUID inviteId,
            String eventFingerprint
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

        String fingerprint =
                (eventFingerprint != null && !eventFingerprint.isBlank())
                        ? eventFingerprint
                        : EventFingerprint.of(List.of(
                        STREAM,
                        "SUCCESS",
                        inviteId.toString(),
                        subjectId,
                        tenantId.toString(),
                        correlationId,
                        String.valueOf(eventTimestamp.toEpochMilli())
                ));

        CorrelationSource correlationSource = resolveCorrelationSource();

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

        final long startNs = System.nanoTime();
        try {
            AuditPartition partition =
                    partitionResolver.onboarding(tenantId.toString());

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(partition, canonicalMaterial);

            repository.save(new OnboardingAuditEvent(
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
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));

            metrics.incrementSuccess(STREAM, EXEC_CTX);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

        } catch (DataIntegrityViolationException ignored) {
            metrics.incrementDedup(STREAM, EXEC_CTX);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

        } catch (Exception ex) {
            metrics.incrementFailure(STREAM, EXEC_CTX, ex);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

            log.error("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "onboarding_audit_record_failed"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", STREAM),
                    kv("audit.partition", "TENANT"),
                    kv("audit.result", AuditResult.SUCCESS.name()),
                    kv("execution.context", EXEC_CTX),
                    kv("correlation.id", correlationId),
                    kv("correlation.source", correlationSource.name()),
                    kv("tenant.id", tenantId),
                    kv("invite.id", inviteId),
                    kv("subject.id", subjectId),
                    kv("onboarding.outcome", "SUCCESS"),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            UUID actorUserId,
            UUID tenantId,
            UUID inviteId,
            String failureReason,
            String eventFingerprint
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

        String fingerprint =
                (eventFingerprint != null && !eventFingerprint.isBlank())
                        ? eventFingerprint
                        : EventFingerprint.of(List.of(
                        STREAM,
                        "FAILURE",
                        inviteId.toString(),
                        tenantId.toString(),
                        resolvedReason,
                        correlationId,
                        String.valueOf(eventTimestamp.toEpochMilli())
                ));

        CorrelationSource correlationSource = resolveCorrelationSource();

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

        final long startNs = System.nanoTime();
        try {
            AuditPartition partition =
                    partitionResolver.onboarding(tenantId.toString());

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(partition, canonicalMaterial);

            repository.save(new OnboardingAuditEvent(
                    eventTimestamp,
                    actorUserId,
                    null,
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
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));

            metrics.incrementSuccess(STREAM, EXEC_CTX);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

        } catch (DataIntegrityViolationException ignored) {
            metrics.incrementDedup(STREAM, EXEC_CTX);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

        } catch (Exception ex) {
            metrics.incrementFailure(STREAM, EXEC_CTX, ex);
            metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

            log.error("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "onboarding_audit_record_failed"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", STREAM),
                    kv("audit.partition", "TENANT"),
                    kv("audit.result", AuditResult.FAILED.name()),
                    kv("execution.context", EXEC_CTX),
                    kv("correlation.id", correlationId),
                    kv("correlation.source", correlationSource.name()),
                    kv("tenant.id", tenantId),
                    kv("invite.id", inviteId),
                    kv("onboarding.outcome", "FAILURE"),
                    kv("onboarding.failure_reason", resolvedReason),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
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

    private static CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(MDC.get(RequestCorrelationIdFilter.MDC_SOURCE_KEY))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private static void requireNonNull(Object v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}