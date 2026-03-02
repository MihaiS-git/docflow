package com.brutecx.docflow_backend.security.handler;

import com.brutecx.docflow_backend.api.error.ErrorCode;
import com.brutecx.docflow_backend.api.error.ErrorResponse;
import com.brutecx.docflow_backend.api.error.LifecycleAccessDeniedException;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.lifecycle.ILifecycleDeniedAuditService;
import com.brutecx.docflow_backend.audit.rbac.IRbacDeniedAuditService;
import com.brutecx.docflow_backend.logging.InfraEventActions;
import com.brutecx.docflow_backend.logging.InfraEventLogger;
import com.brutecx.docflow_backend.logging.InfraEventOutcome;
import com.brutecx.docflow_backend.logging.InfraEventType;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final ILifecycleDeniedAuditService lifecycleDeniedAuditService;
    private final IRbacDeniedAuditService rbacDeniedAuditService;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException ex
    ) throws IOException {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        String correlationId = MDC.get(RequestCorrelationIdFilter.MDC_KEY);

        String subjectId = null;
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidcUser) {
            subjectId = oidcUser.getSubject();
        }

        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();

        ErrorCode errorCodeEnum;

        if (ex instanceof LifecycleAccessDeniedException lifecycleEx) {

            String lifecycleCode = lifecycleEx.getErrorCode();

            try {
                errorCodeEnum = ErrorCode.valueOf(lifecycleCode);
            } catch (IllegalArgumentException ignored) {
                errorCodeEnum = ErrorCode.INTERNAL_SERVER_ERROR;
            }

            String fingerprint = EventFingerprint.of(List.of(
                    "LIFECYCLE_DENIED",
                    lifecycleCode,
                    uri,
                    correlationId
            ));

            try {
                lifecycleDeniedAuditService.record(
                        subjectId,
                        lifecycleCode,
                        httpMethod,
                        uri,
                        fingerprint
                );

                InfraEventLogger.log(
                        InfraEventType.AUTHORIZATION,
                        InfraEventActions.AUTHZ_LIFECYCLE_DENIED_AUDIT_WRITE,
                        InfraEventOutcome.SUCCESS,
                        null,
                        null,
                        StructuredArguments.kv("actor.subject_id", subjectId),
                        StructuredArguments.kv("http.method", httpMethod),
                        StructuredArguments.kv("http.path", uri)
                );

            } catch (Exception auditEx) {
                if (!(auditEx instanceof DataIntegrityViolationException)) {
                    InfraEventLogger.log(
                            InfraEventType.AUTHORIZATION,
                            InfraEventActions.AUTHZ_LIFECYCLE_DENIED_AUDIT_WRITE,
                            InfraEventOutcome.FAILURE,
                            "LifecycleDenied audit write failed",
                            auditEx
                    );
                }
            }

        } else {

            errorCodeEnum = ErrorCode.FORBIDDEN;

            String fingerprint = EventFingerprint.of(List.of(
                    "RBAC_DENIED",
                    uri,
                    correlationId
            ));

            try {
                rbacDeniedAuditService.record(
                        subjectId,
                        httpMethod,
                        uri,
                        fingerprint
                );

                InfraEventLogger.log(
                        InfraEventType.AUTHORIZATION,
                        InfraEventActions.AUTHZ_RBAC_DENIED_AUDIT_WRITE,
                        InfraEventOutcome.SUCCESS,
                        null,
                        null,
                        StructuredArguments.kv("actor.subject_id", subjectId),
                        StructuredArguments.kv("http.method", httpMethod),
                        StructuredArguments.kv("http.path", uri)
                );

            } catch (Exception auditEx) {
                if (!(auditEx instanceof DataIntegrityViolationException)) {
                    InfraEventLogger.log(
                            InfraEventType.AUTHORIZATION,
                            InfraEventActions.AUTHZ_RBAC_DENIED_AUDIT_WRITE,
                            InfraEventOutcome.FAILURE,
                            "RbacDenied audit write failed",
                            auditEx
                    );
                }
            }
        }

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                errorCodeEnum,
                ex.getMessage(),
                uri
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}