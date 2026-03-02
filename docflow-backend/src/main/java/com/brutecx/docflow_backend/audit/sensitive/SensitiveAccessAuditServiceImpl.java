package com.brutecx.docflow_backend.audit.sensitive;

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
import java.util.Objects;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class SensitiveAccessAuditServiceImpl implements ISensitiveAccessAuditService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = SensitiveAccessCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();

    private final SensitiveAccessAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final SensitiveAccessCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditWriteFailureMetrics metrics;
    private final AuditPartitionResolver partitionResolver;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID actorUserId,
            String actorExternalSubjectId,
            UUID tenantId,
            SensitiveAccessSubjectType subjectType,
            String subjectId,
            String resource,
            String action,
            String resourcePath,
            String ignoredCorrelationId,
            String ignoredIp,
            String ignoredUserAgent,
            String reasonCode,
            String reasonDetail,
            SensitiveDataClassification dataClassification,
            String eventFingerprint
    ) {

        ensureHttpContext();

        if (tenantId == null) {
            throw new IllegalStateException("SensitiveAccessAudit requires tenantId");
        }
        Objects.requireNonNull(subjectType, "SensitiveAccessAudit requires subjectType");

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        String resolvedSubjectId = normalizeOr(subjectId, "UNKNOWN");
        String resolvedResource = normalizeOr(resource, "UNKNOWN");
        String resolvedAction = normalizeOr(action, "UNKNOWN");
        String resolvedPath = normalizeOr(resourcePath, "UNKNOWN");
        String resolvedReason = normalizeOr(reasonCode, "NONE");

        SensitiveDataClassification classification =
                (dataClassification != null)
                        ? dataClassification
                        : SensitiveDataClassification.INTERNAL;

        Instant eventTimestamp = Instant.now();

        String fingerprint =
                (eventFingerprint != null && !eventFingerprint.isBlank())
                        ? eventFingerprint
                        : EventFingerprint.of(List.of(
                        STREAM,
                        String.valueOf(actorUserId),
                        tenantId.toString(),
                        subjectType.name(),
                        resolvedSubjectId,
                        resolvedResource,
                        resolvedAction,
                        resolvedPath,
                        resolvedReason,
                        classification.name(),
                        correlationId,
                        String.valueOf(eventTimestamp.toEpochMilli())
                ));

        CorrelationSource correlationSource = resolveCorrelationSource();

        SensitiveAccessCanonicalMaterialBuilder.Input canonicalInput =
                new SensitiveAccessCanonicalMaterialBuilder.Input(
                        eventTimestamp,
                        actorUserId,
                        actorExternalSubjectId,
                        tenantId,
                        subjectType,
                        resolvedSubjectId,
                        resolvedResource,
                        resolvedAction,
                        resolvedPath,
                        correlationId,
                        correlationSource.name(),
                        EXEC_CTX,
                        AuditResult.SUCCESS.name(),
                        ctx.ip(),
                        ctx.userAgent(),
                        resolvedReason,
                        reasonDetail,
                        classification,
                        fingerprint
                );

        String canonicalMaterial =
                canonicalMaterialBuilder.buildCanonicalMaterial(canonicalInput);

        final long startNs = System.nanoTime();
        try {

            AuditPartition partition =
                    partitionResolver.sensitiveAccess(tenantId.toString());

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            partition,
                            canonicalMaterial
                    );

            SensitiveAccessAuditEvent entity =
                    new SensitiveAccessAuditEvent(
                            eventTimestamp,
                            actorUserId,
                            actorExternalSubjectId,
                            tenantId,
                            subjectType,
                            resolvedSubjectId,
                            resolvedResource,
                            resolvedAction,
                            resolvedPath,
                            correlationId,
                            correlationSource,
                            ExecutionContext.HTTP,
                            AuditResult.SUCCESS,
                            ctx.ip(),
                            ctx.userAgent(),
                            resolvedReason,
                            reasonDetail,
                            classification,
                            fingerprint,
                            chain.chainVersion(),
                            chain.prevHash(),
                            chain.eventHash()
                    );

            repository.saveAndFlush(entity);

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
                    kv("event.action", "sensitive_access_audit_record_failed"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", STREAM),
                    kv("audit.partition", "TENANT"),
                    kv("audit.result", AuditResult.SUCCESS.name()),
                    kv("execution.context", EXEC_CTX),
                    kv("correlation.id", correlationId),
                    kv("correlation.source", correlationSource.name()),
                    kv("tenant.id", tenantId),
                    kv("actor.user_id", actorUserId),
                    kv("actor.subject_id", actorExternalSubjectId),
                    kv("subject.type", subjectType.name()),
                    kv("subject.id", resolvedSubjectId),
                    kv("resource", resolvedResource),
                    kv("action", resolvedAction),
                    kv("resource.path", resolvedPath),
                    kv("reason.code", resolvedReason),
                    kv("data.classification", classification.name()),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );
        }
    }

    private void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("SensitiveAccessAudit invoked outside HTTP request context");
        }
    }

    private String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId");
        }
        return corr;
    }

    private CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(MDC.get(RequestCorrelationIdFilter.MDC_SOURCE_KEY))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private static String normalizeOr(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }
}