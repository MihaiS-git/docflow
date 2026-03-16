package com.brutecx.docflow_backend.security.handler;

import com.brutecx.docflow_backend.api.error.ErrorCode;
import com.brutecx.docflow_backend.api.error.ErrorResponse;
import com.brutecx.docflow_backend.api.error.ErrorTemplates;
import com.brutecx.docflow_backend.api.error.LifecycleAccessDeniedException;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.security.SecuritySubjectResolver;
import com.brutecx.docflow_backend.security.audit.AuthorizationDeniedAuditExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AuthorizationDeniedAuditExecutor auditExecutor;
    private final AuditRequestContextExtractor contextExtractor;
    private final SecuritySubjectResolver subjectResolver;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException ex
    ) throws IOException {
        AuditRequestContext ctx = contextExtractor.fromCurrentRequest();

        String correlationId = ctx.correlationId();
        String subjectId = subjectResolver.resolveSubjectId();

        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();

        ErrorCode errorCode;

        if (ex instanceof LifecycleAccessDeniedException lifecycleEx) {
            errorCode = lifecycleEx.errorCode();

            auditExecutor.lifecycle(
                    subjectId,
                    errorCode.name(),
                    httpMethod,
                    uri,
                    correlationId
            );
        } else {
            errorCode = ErrorCode.FORBIDDEN;
            auditExecutor.rbac(
                    subjectId,
                    httpMethod,
                    uri,
                    correlationId
            );
        }

        ErrorResponse body = ErrorResponse.fromTemplate(
                ErrorTemplates.FORBIDDEN,
                ex.getMessage(),
                uri
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}