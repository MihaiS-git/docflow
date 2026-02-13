package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEvent;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEventRepository;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.audit.tamper.AuditChainHasher;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class CredentialLifecycleAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int VERIFY_BATCH_SIZE = 1_000;

    private static final String STREAM = "CREDENTIAL";

    // Frozen allowlist for this stream (contract-safe, minimal)
    private static final List<String> ALLOWED_SORT_FIELDS = List.of("timestamp");

    private final CredentialLifecycleAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;

    @Value("${docflow.audit.chain.secret:}")
    private String chainSecret;

    /* =====================================================
       QUERY (cursor primary, offset optional)
       ===================================================== */

    @Transactional(readOnly = true)
    public Page<CredentialLifecycleAuditDTO> query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectExternalId,
            AuditResult result,
            Instant cursorTimestamp,
            UUID cursorId,
            int page,
            int size,
            String sortField,
            Sort.Direction direction
    ) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Sort.Direction dir = direction != null ? direction : Sort.Direction.DESC;
        String safeSort = validateSortField(sortField);

        // Deterministic ordering: always timestamp + id
        Sort sort = Sort.by(
                new Sort.Order(dir, safeSort),
                new Sort.Order(dir, "id")
        );

        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                from != null ? CredentialLifecycleAuditSpecifications.timestampFrom(from) : null,
                to != null ? CredentialLifecycleAuditSpecifications.timestampTo(to) : null,
                correlationId != null && !correlationId.isBlank()
                        ? CredentialLifecycleAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                subjectExternalId != null && !subjectExternalId.isBlank()
                        ? CredentialLifecycleAuditSpecifications.hasSubjectExternalId(subjectExternalId)
                        : null,
                result != null ? CredentialLifecycleAuditSpecifications.hasResult(result) : null,
                (cursorTimestamp != null && cursorId != null)
                        ? CredentialLifecycleAuditSpecifications.cursorAfter(
                        cursorTimestamp,
                        cursorId,
                        dir == Sort.Direction.ASC
                )
                        : null
        );

        Page<CredentialLifecycleAuditEvent> pageResult =
                repository.findAll(spec, pageable);

        recordSensitiveAccess();

        return pageResult.map(CredentialLifecycleAuditDTO::from);
    }

    private static String validateSortField(String sortField) {
        if (sortField == null || sortField.isBlank()) {
            return "timestamp";
        }
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("Unsupported sort field");
        }
        return sortField;
    }

    /* =====================================================
       EXPORT (CSV/JSONL use this)
       - requires from/to
       - deterministic ASC timestamp + id
       - hard cap
       ===================================================== */

    @Transactional(readOnly = true)
    public void export(
            Instant from,
            Instant to,
            String correlationId,
            String subjectExternalId,
            AuditResult result,
            Consumer<CredentialLifecycleAuditEvent> consumer
    ) {

        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required for export");
        }

        long exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorUuid = null;

        while (true) {

            Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                    CredentialLifecycleAuditSpecifications.timestampFrom(from),
                    CredentialLifecycleAuditSpecifications.timestampTo(to),
                    correlationId != null && !correlationId.isBlank()
                            ? CredentialLifecycleAuditSpecifications.hasCorrelationId(correlationId)
                            : null,
                    subjectExternalId != null && !subjectExternalId.isBlank()
                            ? CredentialLifecycleAuditSpecifications.hasSubjectExternalId(subjectExternalId)
                            : null,
                    result != null ? CredentialLifecycleAuditSpecifications.hasResult(result) : null,
                    (cursorTimestamp != null && cursorUuid != null)
                            ? CredentialLifecycleAuditSpecifications.cursorAfter(
                            cursorTimestamp,
                            cursorUuid,
                            true
                    )
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    EXPORT_BATCH_SIZE,
                    Sort.by(
                            Sort.Order.asc("timestamp"),
                            Sort.Order.asc("id")
                    )
            );

            Page<CredentialLifecycleAuditEvent> batch =
                    repository.findAll(spec, pageable);

            if (batch.isEmpty()) {
                break;
            }

            for (CredentialLifecycleAuditEvent e : batch.getContent()) {

                consumer.accept(e);
                exported++;

                if (exported >= EXPORT_MAX_ROWS) {
                    recordSensitiveAccess();
                    return;
                }

                cursorTimestamp = e.getTimestamp();
                cursorUuid = e.getId();
            }
        }

        recordSensitiveAccess();
    }

    /* =====================================================
       VERIFY
       - requires from/to
       - deterministic ASC traversal
       - validates continuity per partitionKey (subjectExternalId / GLOBAL)
       - recomputes event_hash using your exact Keycloak pull job material
       ===================================================== */

    @Transactional(readOnly = true)
    public CredentialLifecycleAuditVerificationResultDTO verify(
            Instant from,
            Instant to,
            String correlationId,
            String subjectExternalId
    ) {

        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required for verification");
        }
        if (chainSecret == null || chainSecret.isBlank()) {
            recordSensitiveAccess();
            return CredentialLifecycleAuditVerificationResultDTO.failure(
                    0,
                    null,
                    "Audit chain secret not configured"
            );
        }

        long verified = 0;

        // Continuity is per partitionKey (as used by AuditChainService.nextHash STREAM + partitionKey)
        Map<String, String> lastHashByPartition = new HashMap<>();

        Instant cursorTimestamp = null;
        UUID cursorUuid = null;

        while (true) {

            Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                    CredentialLifecycleAuditSpecifications.timestampFrom(from),
                    CredentialLifecycleAuditSpecifications.timestampTo(to),
                    correlationId != null && !correlationId.isBlank()
                            ? CredentialLifecycleAuditSpecifications.hasCorrelationId(correlationId)
                            : null,
                    subjectExternalId != null && !subjectExternalId.isBlank()
                            ? CredentialLifecycleAuditSpecifications.hasSubjectExternalId(subjectExternalId)
                            : null,
                    (cursorTimestamp != null && cursorUuid != null)
                            ? CredentialLifecycleAuditSpecifications.cursorAfter(
                            cursorTimestamp,
                            cursorUuid,
                            true
                    )
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(
                            Sort.Order.asc("timestamp"),
                            Sort.Order.asc("id")
                    )
            );

            Page<CredentialLifecycleAuditEvent> batch =
                    repository.findAll(spec, pageable);

            if (batch.isEmpty()) {
                break;
            }

            for (CredentialLifecycleAuditEvent e : batch.getContent()) {

                String partitionKey =
                        (e.getSubjectExternalId() != null && !e.getSubjectExternalId().isBlank())
                                ? e.getSubjectExternalId()
                                : "GLOBAL";

                String expectedPrev = lastHashByPartition.get(partitionKey);
                if (expectedPrev != null && !Objects.equals(e.getPrevEventHash(), expectedPrev)) {
                    recordSensitiveAccess();
                    return CredentialLifecycleAuditVerificationResultDTO.failure(
                            verified,
                            e.getId(),
                            "Chain continuity mismatch partition=" + partitionKey
                    );
                }

                // Recompute EXACTLY like KeycloakCredentialLifecycleEventPullJob
                String sessionId =
                        (e.getSessionId() != null && !e.getSessionId().isBlank())
                                ? e.getSessionId()
                                : "-";

                String subject = (e.getSubjectExternalId() != null) ? e.getSubjectExternalId() : "GLOBAL";
                String clientId = (e.getClientId() != null) ? e.getClientId() : "UNKNOWN";

                String fingerprint = e.getEventFingerprint();

                String eventMaterial = String.join("|",
                        STREAM,
                        e.getEventType().name(),
                        subject,
                        clientId,
                        sessionId,
                        String.valueOf(e.getTimestamp().toEpochMilli()),
                        fingerprint
                );

                String material = "v1|" + STREAM + "|" + partitionKey + "|" + e.getPrevEventHash() + "|" + eventMaterial;

                String recomputed = AuditChainHasher.hmacSha256Hex(chainSecret, material);

                if (!Objects.equals(recomputed, e.getEventHash())) {
                    recordSensitiveAccess();
                    return CredentialLifecycleAuditVerificationResultDTO.failure(
                            verified,
                            e.getId(),
                            "Event hash mismatch partition=" + partitionKey
                    );
                }

                lastHashByPartition.put(partitionKey, e.getEventHash());
                verified++;

                cursorTimestamp = e.getTimestamp();
                cursorUuid = e.getId();
            }
        }

        recordSensitiveAccess();
        return CredentialLifecycleAuditVerificationResultDTO.success(verified);
    }

    /* =====================================================
       Sensitive Read Logging
       ===================================================== */

    private void recordSensitiveAccess() {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenantId = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                "CREDENTIAL_LIFECYCLE",
                actor.getId().toString(),
                rootTenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                "CREDENTIAL_LIFECYCLE",
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read credential lifecycle audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }
}
