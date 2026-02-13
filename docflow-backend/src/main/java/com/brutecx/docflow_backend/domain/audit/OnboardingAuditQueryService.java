package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEvent;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEventRepository;
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
public class OnboardingAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final String STREAM = "ONBOARDING";

    private final OnboardingAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;

    @Value("${docflow.audit.chain.secret:}")
    private String chainSecret;

    /* ===================== QUERY ===================== */

    @Transactional(readOnly = true)
    public Page<OnboardingAuditDTO> query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            Instant cursorTimestamp,
            UUID cursorId,
            int page,
            int size,
            String sortField,
            Sort.Direction direction
    ) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort.Direction dir = direction != null ? direction : Sort.Direction.DESC;
        String safeSort = validateSort(sortField);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                safeSize,
                Sort.by(
                        new Sort.Order(dir, "timestamp"),
                        new Sort.Order(dir, safeSort),
                        new Sort.Order(dir, "id")
                )
        );

        Specification<OnboardingAuditEvent> spec = Specification.allOf(
                from != null ? OnboardingAuditSpecifications.timestampFrom(from) : null,
                to != null ? OnboardingAuditSpecifications.timestampTo(to) : null,
                (correlationId != null && !correlationId.isBlank())
                        ? OnboardingAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                (subjectId != null && !subjectId.isBlank())
                        ? OnboardingAuditSpecifications.hasSubjectId(subjectId)
                        : null,
                (tenantId != null)
                        ? OnboardingAuditSpecifications.hasTenantId(tenantId)
                        : null,
                (cursorTimestamp != null && cursorId != null)
                        ? OnboardingAuditSpecifications.cursorAfter(
                        cursorTimestamp,
                        cursorId,
                        dir == Sort.Direction.ASC
                )
                        : null
        );

        Page<OnboardingAuditEvent> result = repository.findAll(spec, pageable);

        recordSensitiveAccess();

        return result.map(OnboardingAuditDTO::from);
    }

    private static String validateSort(String sortField) {
        if (sortField == null || sortField.isBlank()) {
            return "timestamp";
        }
        // Allowlist: onboarding supports timestamp only (id is always the deterministic tiebreaker)
        if (!"timestamp".equals(sortField)) {
            throw new IllegalArgumentException("Unsupported sort field");
        }
        return sortField;
    }

    /* ===================== EXPORT ===================== */

    @Transactional(readOnly = true)
    public void export(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            Consumer<OnboardingAuditEvent> consumer
    ) {

        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to required");
        }

        long exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorUuid = null;

        while (true) {
            Pageable pageable = PageRequest.of(
                    0,
                    EXPORT_BATCH_SIZE,
                    Sort.by(
                            Sort.Order.asc("timestamp"),
                            Sort.Order.asc("id")
                    )
            );

            Specification<OnboardingAuditEvent> spec = Specification.allOf(
                    OnboardingAuditSpecifications.timestampFrom(from),
                    OnboardingAuditSpecifications.timestampTo(to),
                    (correlationId != null && !correlationId.isBlank())
                            ? OnboardingAuditSpecifications.hasCorrelationId(correlationId)
                            : null,
                    (subjectId != null && !subjectId.isBlank())
                            ? OnboardingAuditSpecifications.hasSubjectId(subjectId)
                            : null,
                    (tenantId != null)
                            ? OnboardingAuditSpecifications.hasTenantId(tenantId)
                            : null,
                    (cursorTimestamp != null && cursorUuid != null)
                            ? OnboardingAuditSpecifications.cursorAfter(cursorTimestamp, cursorUuid, true)
                            : null
            );

            Page<OnboardingAuditEvent> batch = repository.findAll(spec, pageable);

            if (batch.isEmpty()) break;

            for (OnboardingAuditEvent e : batch.getContent()) {
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

    /* ===================== VERIFY ===================== */

    @Transactional(readOnly = true)
    public OnboardingAuditVerificationResultDTO verify(
            Instant from,
            Instant to
    ) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to required");
        }

        long verified = 0;
        Instant cursorTimestamp = null;
        UUID cursorUuid = null;

        if (chainSecret == null || chainSecret.isBlank()) {
            recordSensitiveAccess();
            return OnboardingAuditVerificationResultDTO.failure(
                    0,
                    null,
                    "Audit chain secret missing (docflow.audit.chain.secret)"
            );
        }

        // Chain is partitioned per tenant for onboarding events.
        Map<UUID, String> lastHashByTenant = new HashMap<>();

        while (true) {
            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Specification<OnboardingAuditEvent> spec = Specification.allOf(
                    OnboardingAuditSpecifications.timestampFrom(from),
                    OnboardingAuditSpecifications.timestampTo(to),
                    (cursorTimestamp != null && cursorUuid != null)
                            ? OnboardingAuditSpecifications.cursorAfter(cursorTimestamp, cursorUuid, true)
                            : null
            );

            Page<OnboardingAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) break;

            for (OnboardingAuditEvent e : batch.getContent()) {

                UUID partitionTenantId = e.getTenantId();
                String previousHash = lastHashByTenant.get(partitionTenantId);

                if (previousHash != null && !Objects.equals(e.getPrevEventHash(), previousHash)) {
                    recordSensitiveAccess();
                    return OnboardingAuditVerificationResultDTO.failure(
                            verified,
                            e.getId(),
                            "Chain continuity mismatch tenantId=" + partitionTenantId +
                                    " expectedPrev=" + previousHash +
                                    " actualPrev=" + e.getPrevEventHash()
                    );
                }

                // NOTE: This material must match the write-path canonical material for onboarding.
                // Given only the event model here, we anchor on stable fields + timestamp millis + fingerprint.
                String eventMaterial = String.join("|",
                        STREAM,
                        e.getTenantId().toString(),
                        e.getInviteId().toString(),
                        e.getSubjectId() != null ? e.getSubjectId() : "-",
                        e.getOutcome().name(),
                        e.getResult().name(),
                        String.valueOf(e.getTimestamp().toEpochMilli()),
                        e.getEventFingerprint()
                );

                String chainMaterial =
                        "v1|" + STREAM + "|" +
                                e.getTenantId() + "|" +
                                e.getPrevEventHash() + "|" +
                                eventMaterial;

                String recomputed = AuditChainHasher.hmacSha256Hex(chainSecret, chainMaterial);

                if (!Objects.equals(recomputed, e.getEventHash())) {
                    recordSensitiveAccess();
                    return OnboardingAuditVerificationResultDTO.failure(
                            verified,
                            e.getId(),
                            "Event hash mismatch tenantId=" + partitionTenantId
                    );
                }

                lastHashByTenant.put(partitionTenantId, e.getEventHash());
                verified++;

                cursorTimestamp = e.getTimestamp();
                cursorUuid = e.getId();
            }
        }

        recordSensitiveAccess();
        return OnboardingAuditVerificationResultDTO.success(verified);
    }

    /* ===================== SENSITIVE ACCESS ===================== */

    private void recordSensitiveAccess() {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenantId = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                STREAM,
                actor.getId().toString(),
                rootTenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                STREAM,
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read onboarding audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }
}
