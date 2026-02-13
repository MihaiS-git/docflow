package com.brutecx.docflow_backend.security.handler;

import com.brutecx.docflow_backend.api.error.ErrorCode;
import com.brutecx.docflow_backend.api.error.ErrorResponse;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.unauth.IUnauthenticatedAccessAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
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
        } catch (Exception e) {
            String corr = MDC.get("correlationId");
            log.error(
                    "UNAUTH AUDIT FAILURE correlationId={} method={} uri={}",
                    corr,
                    httpMethod,
                    uri,
                    e
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
