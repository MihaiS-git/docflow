package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.canonical.CanonicalJsonService;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditEventServiceImpl implements IAdminAuditEventService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String STREAM = AdminAuditCanonicalMaterialBuilder.STREAM;

    private final AdminAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final UserService userService;
    private final AdminAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditCanonicalVersionProvider canonicalVersionProvider;
    private final CanonicalJsonService canonicalJsonService;

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

        if (actionType == null) throw new IllegalArgumentException("actionType is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (subjectId == null || subjectId.isBlank()) throw new IllegalArgumentException("subjectId is required");

        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();
        String correlationId = requireCorrelation(ctx);

        UUID actorUserId = resolveActor(actionType, targetUserId);

        CorrelationSource correlationSource = resolveCorrelationSource();
        AuditResult result = resolveResult(actionType, metadata);

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

        String canonicalMaterial = canonicalMaterialBuilder.buildCanonicalMaterial(
                new AdminAuditCanonicalMaterialBuilder.Input(
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

        /*
         * Partition rules:
         * AdminAudit → TENANT
         */

        AuditPartition partition =
                AuditPartition.tenant(STREAM, tenantId.toString());

        try {

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            partition,
                            canonicalMaterial
                    );

            repository.save(new AdminAuditEvent(
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
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            ));

        } catch (Exception ex) {
            log.error(
                    "ADMIN AUDIT FAILURE correlationId={} actionType={} tenantId={}",
                    correlationId,
                    actionType,
                    tenantId,
                    ex
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

        if (metadata != null) {
            fp.add(canonicalJsonService.toCanonicalJson(metadata));
        } else {
            fp.add("-");
        }

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
        return "GENERATED".equalsIgnoreCase(MDC.get("correlationSource"))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private AuditResult resolveResult(AdminAuditActionType actionType, AdminAuditMetadata metadata) {
        if (actionType == AdminAuditActionType.TENANT_CREATE_FAILED) return AuditResult.FAILED;
        if (actionType == AdminAuditActionType.TENANT_MUTATION_DENIED) return AuditResult.DENIED;

        if (metadata instanceof InviteAuditMetadata invite) {
            return invite.outcome() == InviteOutcome.FAILURE ? AuditResult.FAILED : AuditResult.SUCCESS;
        }

        return AuditResult.SUCCESS;
    }
}
