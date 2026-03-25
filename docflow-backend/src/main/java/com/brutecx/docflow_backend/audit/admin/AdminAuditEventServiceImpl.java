package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.AuditStreamExecutor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.canonical.CanonicalJsonService;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.brutecx.docflow_backend.logging.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class AdminAuditEventServiceImpl implements IAdminAuditEventService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String STREAM = AdminAuditCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();

    private final AdminAuditEventRepository repository;
    private final AuditRequestContextExtractor contextExtractor;
    private final UserService userService;
    private final AdminAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditCanonicalVersionProvider canonicalVersionProvider;
    private final CanonicalJsonService canonicalJsonService;
    private final AuditPartitionResolver partitionResolver;
    private final AuditStreamExecutor executor;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AdminAuditActionType actionType,
            UUID tenantId,
            String subjectId,
            UUID targetUserId,
            AdminAuditMetadata metadata
    ) {
        ensureHttpContext();

        if (actionType == null) {
            throw new IllegalArgumentException("actionType is required");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        UUID actorUserId = resolveActor(actionType, targetUserId);
        CorrelationSource correlationSource = resolveCorrelationSource();
        AuditResult result = resolveResult(actionType);
        Instant eventTimestamp = Instant.now();

        String fingerprint = buildFingerprint(
                actionType,
                result,
                tenantId,
                actorUserId,
                subjectId,
                targetUserId,
                metadata,
                correlationId
        );

        String canonicalMaterial =
                canonicalMaterialBuilder.buildCanonicalMaterial(
                        new AdminAuditCanonicalMaterialBuilder.Input(
                                eventTimestamp,
                                actorUserId,
                                subjectId,
                                tenantId,
                                actionType,
                                result,
                                correlationId,
                                targetUserId,
                                metadata,
                                fingerprint
                        )
                );

        AuditPartition partition = partitionResolver.admin(tenantId.toString());

        try {
            AuditStreamExecutor.WriteOutcome outcome =
                    executor.execute(
                            STREAM,
                            EXEC_CTX,
                            partition,
                            canonicalMaterial,
                            repository,
                            prepared -> new AdminAuditEvent(
                                    eventTimestamp,
                                    actorUserId,
                                    ctx.ip(),
                                    ctx.userAgent(),
                                    correlationId,
                                    correlationSource,
                                    ExecutionContext.HTTP,
                                    result,
                                    subjectId,
                                    tenantId,
                                    actionType,
                                    targetUserId,
                                    metadata,
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
                        kv("event.action", "admin_audit_deduplicated"),
                        kv("audit.stream", STREAM),
                        kv("tenant.id", tenantId),
                        kv("subject.id", subjectId),
                        kv("action.type", actionType.name()),
                        kv("audit.result", result.name()),
                        kv("correlation.id", correlationId)
                );
            }
        } catch (Exception ex) {
            SecurityAuditLogger.auditFailure(
                    "admin_audit_record_failed",
                    STREAM,
                    "TENANT",
                    tenantId,
                    ex,
                    correlationId
            );
            throw ex;
        }
    }

    private UUID resolveActor(AdminAuditActionType actionType, UUID targetUserId) {
        try {
            User actor = userService.getRequiredCurrentUser();
            return actor.getId();
        } catch (IllegalStateException ex) {
            if (actionType == AdminAuditActionType.BOOTSTRAP_ACTIVATED && targetUserId != null) {
                return targetUserId;
            }
            throw ex;
        }
    }

    private String buildFingerprint(
            AdminAuditActionType actionType,
            AuditResult result,
            UUID tenantId,
            UUID actorUserId,
            String subjectId,
            UUID targetUserId,
            AdminAuditMetadata metadata,
            String correlationId
    ) {
        int cv = canonicalVersionProvider.canonicalVersion();

        List<String> fp = new ArrayList<>();
        fp.add(STREAM);
        fp.add("CV=" + cv);
        fp.add(actionType.name());
        fp.add(result.name());
        fp.add(tenantId.toString());
        fp.add(actorUserId.toString());
        fp.add(subjectId);
        fp.add(correlationId);
        fp.add(targetUserId != null ? targetUserId.toString() : "-");
        fp.add(metadata != null ? metadata.getClass().getSimpleName() : "-");
        fp.add(metadata != null ? canonicalJsonService.toCanonicalJson(metadata) : "-");

        return EventFingerprint.of(fp);
    }

    private static void ensureHttpContext() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            throw new IllegalStateException("AdminAudit invoked outside HTTP request context");
        }
    }

    private static String requireCorrelation(AuditRequestContext ctx) {
        String corr = ctx.correlationId();
        if (corr == null || corr.isBlank()) {
            throw new IllegalStateException("Missing correlationId for admin audit event");
        }
        return corr;
    }

    private static CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(MDC.get("correlation.source"))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private AuditResult resolveResult(AdminAuditActionType actionType) {
        return switch (actionType) {
            case TENANT_MUTATION_DENIED -> AuditResult.DENIED;
            case TENANT_CREATE_FAILED, RETENTION_POLICY_UPSERT_FAILED, INVITE_FAILED, USER_LOCK_FAILED,
                 USER_DISABLE_FAILED, USER_ACTIVATE_FAILED, ROLE_ASSIGN_FAILED, ROLE_REVOKE_FAILED,
                 INVITE_SUBJECT_BIND_FAILED, INVITE_EXPIRE_FAILED -> AuditResult.FAILED;
            default -> AuditResult.SUCCESS;
        };
    }
}