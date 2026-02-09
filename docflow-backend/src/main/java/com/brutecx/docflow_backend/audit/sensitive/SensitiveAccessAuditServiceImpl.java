package com.brutecx.docflow_backend.audit.sensitive;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SensitiveAccessAuditServiceImpl
        implements ISensitiveAccessAuditService {

    private final SensitiveAccessAuditEventRepository repository;
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

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
            String correlationId,

            String ip,
            String userAgent,
            String reasonCode,
            String reasonDetail,
            SensitiveDataClassification dataClassification,
            String eventFingerprint
    ) {

        try {
            Provenance provenance = resolveProvenance(correlationId, eventFingerprint);

            repository.saveAndFlush(new SensitiveAccessAuditEvent(
                    actorUserId,
                    actorExternalSubjectId,
                    tenantId,
                    subjectType,
                    subjectId,
                    resource,
                    action,
                    resourcePath,
                    provenance.correlationId,
                    provenance.correlationSource,
                    provenance.executionContext,
                    provenance.result,
                    ip,
                    userAgent,
                    reasonCode,
                    reasonDetail,
                    dataClassification,
                    eventFingerprint
            ));
        } catch (DataIntegrityViolationException ex) {
            log.debug(
                    "SENSITIVE ACCESS AUDIT DEDUPLICATED. correlationId={} fingerprint={}",
                    correlationId,
                    eventFingerprint
            );
        } catch (Exception ex) {
            log.error(
                    "SENSITIVE ACCESS AUDIT FAILURE. correlationId={} subjectType={} subjectId={} resource={} action={}",
                    correlationId,
                    subjectType,
                    subjectId,
                    resource,
                    action,
                    ex
            );
        }
    }

    private Provenance resolveProvenance(String correlationId, String eventFingerprint) {
        // Execution context: if we are on a request thread, it's HTTP; otherwise SYSTEM.
        ExecutionContext executionContext =
                (RequestContextHolder.getRequestAttributes() != null)
                        ? ExecutionContext.HTTP
                        : ExecutionContext.SYSTEM;

        // Result semantics for sensitive reads: SUCCESS (denied audits are recorded elsewhere).
        AuditResult result = AuditResult.SUCCESS;

        // Correlation source:
        // - Prefer explicit MDC label set by RequestCorrelationIdFilter.
        // - If missing but correlationId exists, treat as REQUEST_ID (best available truth).
        // - If missing entirely, generate deterministically and label GENERATED.
        String mdcSource = MDC.get("correlationSource");
        if (correlationId != null && !correlationId.isBlank()) {
            CorrelationSource source =
                    "GENERATED".equalsIgnoreCase(mdcSource)
                            ? CorrelationSource.GENERATED
                            : CorrelationSource.REQUEST_ID;

            return new Provenance(correlationId, source, executionContext, result);
        }

        // No real correlation available → generate deterministic correlation id from fingerprint.
        // This is not random and is explicitly labeled GENERATED.
        String generated = "gen-" + shortSha256(eventFingerprint != null ? eventFingerprint : "NO_FINGERPRINT");
        return new Provenance(generated, CorrelationSource.GENERATED, executionContext, result);
    }

    private static String shortSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // 12 bytes => 24 hex chars, compact but collision-resistant enough for correlation labeling.
            StringBuilder sb = new StringBuilder(24);
            for (int i = 0; i < 12; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // Fallback should never lie: still label GENERATED.
            return "hash_error";
        }
    }

    private record Provenance(
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            AuditResult result
    ) {
    }
}
