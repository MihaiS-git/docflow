package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
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
    private static final String STREAM = "ADMIN_AUDIT";

    private final AdminAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final AuditRequestContextExtractor contextExtractor;
    private final UserService userService;

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

        UUID actorUserId;
        try {
            User actor = userService.getRequiredCurrentUser();
            actorUserId = actor.getId();
        } catch (IllegalStateException ex) {
            // Bootstrap identity mutation case:
            // actor not yet visible in DB due to suspended transaction
            if (actionType == AdminAuditActionType.BOOTSTRAP_ACTIVATED
                    && targetUserId != null) {
                actorUserId = targetUserId;
            } else {
                throw ex;
            }
        }

        CorrelationSource correlationSource = resolveCorrelationSource();
        AuditResult result = resolveResult(actionType, metadata);

        List<String> fp = new ArrayList<>();
        fp.add(STREAM);
        fp.add(actionType.name());
        fp.add(result.name());
        fp.add(tenantId.toString());
        fp.add(actorUserId.toString());
        fp.add(subjectId);
        fp.add(correlationId);
        fp.add(targetUserId != null ? targetUserId.toString() : "-");
        fp.add(metadata != null ? metadata.getClass().getSimpleName() : "-");
        fp.add(metadata != null ? metadata.toString() : "-");

        String fingerprint = EventFingerprint.of(fp);

        String material = String.join("|",
                STREAM,
                actorUserId.toString(),
                subjectId,
                tenantId.toString(),
                actionType.name(),
                result.name(),
                correlationId,
                fingerprint
        );

        try {
            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            STREAM,
                            tenantId.toString(),
                            material
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
