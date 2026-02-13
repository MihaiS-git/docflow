package com.brutecx.docflow_backend.security.handler;

import com.brutecx.docflow_backend.api.error.ErrorCode;
import com.brutecx.docflow_backend.api.error.ErrorResponse;
import com.brutecx.docflow_backend.api.error.LifecycleAccessDeniedException;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.lifecycle.ILifecycleDeniedAuditService;
import com.brutecx.docflow_backend.audit.rbac.IRbacDeniedAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

        log.error(
                "ACCESS DENIED → uri={}, authorities={}, exception={}",
                request.getRequestURI(),
                auth != null ? auth.getAuthorities() : "NO_AUTH",
                ex.getClass().getSimpleName()
        );

        String correlationId = MDC.get("correlationId");

        String subjectId = null;
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidcUser) {
            subjectId = oidcUser.getSubject();
        }

        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();

        ErrorCode errorCodeEnum = ErrorCode.INTERNAL_SERVER_ERROR;

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
            } catch (Exception auditEx) {
                if (auditEx instanceof DataIntegrityViolationException) {
                    log.warn(
                            "Lifecycle audit deduped. correlationId={} reasonCode={} uri={}",
                            correlationId,
                            lifecycleCode,
                            uri
                    );
                }

                log.error(
                        "LIFECYCLE AUDIT FAILURE → correlationId={} subjectId={} reasonCode={} method={} uri={}",
                        correlationId,
                        subjectId,
                        lifecycleCode,
                        httpMethod,
                        uri,
                        auditEx
                );
            }

        } else {

            errorCodeEnum = ErrorCode.INVALID_ARGUMENT;

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
            } catch (Exception auditEx) {
                if (auditEx instanceof DataIntegrityViolationException) {
                    log.warn(
                            "RBAC audit deduped. correlationId={} uri={}",
                            correlationId,
                            uri
                    );
                }

                log.error(
                        "RBAC AUDIT FAILURE → correlationId={} subjectId={} method={} uri={}",
                        correlationId,
                        subjectId,
                        httpMethod,
                        uri,
                        auditEx
                );
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
