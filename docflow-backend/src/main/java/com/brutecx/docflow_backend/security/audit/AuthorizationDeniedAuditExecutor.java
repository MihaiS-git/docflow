package com.brutecx.docflow_backend.security.audit;

import com.brutecx.docflow_backend.audit.lifecycle.ILifecycleDeniedAuditService;
import com.brutecx.docflow_backend.audit.rbac.IRbacDeniedAuditService;
import com.brutecx.docflow_backend.logging.InfraEventActions;
import com.brutecx.docflow_backend.logging.InfraEventLogger;
import com.brutecx.docflow_backend.logging.InfraEventOutcome;
import com.brutecx.docflow_backend.logging.InfraEventType;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorizationDeniedAuditExecutor {

    private final ILifecycleDeniedAuditService lifecycleDeniedAuditService;
    private final IRbacDeniedAuditService rbacDeniedAuditService;

    public void lifecycle(
            String subjectId,
            String lifecycleCode,
            String httpMethod,
            String uri,
            String correlationId
    ) {
        try {
            lifecycleDeniedAuditService.record(
                    subjectId,
                    lifecycleCode,
                    httpMethod,
                    uri,
                    null
            );

            InfraEventLogger.log(
                    InfraEventType.AUTHORIZATION,
                    InfraEventActions.AUTHZ_LIFECYCLE_DENIED_AUDIT_WRITE,
                    InfraEventOutcome.SUCCESS,
                    null,
                    null,
                    StructuredArguments.kv("actor.subject_id", subjectId),
                    StructuredArguments.kv("http.method", httpMethod),
                    StructuredArguments.kv("http.path", uri),
                    StructuredArguments.kv("correlation.id", correlationId)
            );
        } catch (Exception ex) {
            if (!(ex instanceof DataIntegrityViolationException)) {
                InfraEventLogger.log(
                        InfraEventType.AUTHORIZATION,
                        InfraEventActions.AUTHZ_LIFECYCLE_DENIED_AUDIT_WRITE,
                        InfraEventOutcome.FAILURE,
                        "LifecycleDenied audit write failed",
                        ex
                );
            }
        }
    }

    public void rbac(
            String subjectId,
            String httpMethod,
            String uri,
            String correlationId
    ) {
        try {
            rbacDeniedAuditService.record(
                    subjectId,
                    httpMethod,
                    uri
            );

            InfraEventLogger.log(
                    InfraEventType.AUTHORIZATION,
                    InfraEventActions.AUTHZ_RBAC_DENIED_AUDIT_WRITE,
                    InfraEventOutcome.SUCCESS,
                    null,
                    null,
                    StructuredArguments.kv("actor.subject_id", subjectId),
                    StructuredArguments.kv("http.method", httpMethod),
                    StructuredArguments.kv("http.path", uri),
                    StructuredArguments.kv("correlation.id", correlationId)
            );
        } catch (Exception ex) {
            if (!(ex instanceof DataIntegrityViolationException)) {
                InfraEventLogger.log(
                        InfraEventType.AUTHORIZATION,
                        InfraEventActions.AUTHZ_RBAC_DENIED_AUDIT_WRITE,
                        InfraEventOutcome.FAILURE,
                        "RbacDenied audit write failed",
                        ex
                );
            }
        }
    }
}