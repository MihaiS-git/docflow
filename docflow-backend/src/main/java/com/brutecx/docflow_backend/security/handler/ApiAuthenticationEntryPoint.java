package com.brutecx.docflow_backend.security.handler;

import com.brutecx.docflow_backend.api.error.ErrorCode;
import com.brutecx.docflow_backend.api.error.ErrorResponse;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.unauth.IUnauthenticatedAccessAuditService;
import com.brutecx.docflow_backend.logging.InfraEventActions;
import com.brutecx.docflow_backend.logging.InfraEventLogger;
import com.brutecx.docflow_backend.logging.InfraEventOutcome;
import com.brutecx.docflow_backend.logging.InfraEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final IUnauthenticatedAccessAuditService unauthenticatedAccessAuditService;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        String uri = request.getRequestURI();
        String httpMethod = request.getMethod();

        String fingerprint = EventFingerprint.of(List.of(
                "UNAUTH",
                httpMethod != null ? httpMethod : "UNKNOWN",
                uri != null ? uri : "UNKNOWN"
        ));

        try {
            unauthenticatedAccessAuditService.record(
                    httpMethod,
                    uri,
                    fingerprint
            );

            // Structured infra success event
            InfraEventLogger.log(
                    InfraEventType.AUTHENTICATION,
                    InfraEventActions.AUTHN_UNAUTH_ACCESS_AUDIT_WRITE,
                    InfraEventOutcome.SUCCESS,
                    null,
                    null,
                    StructuredArguments.kv("http.method", httpMethod),
                    StructuredArguments.kv("http.path", uri)
            );

        } catch (Exception ex) {

            InfraEventLogger.log(
                    InfraEventType.AUTHENTICATION,
                    InfraEventActions.AUTHN_UNAUTH_ACCESS_AUDIT_WRITE,
                    InfraEventOutcome.FAILURE,
                    "UnauthenticatedAccess audit write failed",
                    ex
            );
        }

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ErrorCode.UNAUTHORIZED,
                "Authentication required",
                uri
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}