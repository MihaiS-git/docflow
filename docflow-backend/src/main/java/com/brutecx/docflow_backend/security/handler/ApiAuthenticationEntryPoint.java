package com.brutecx.docflow_backend.security.handler;

import com.brutecx.docflow_backend.api.error.ErrorResponse;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.unauth.IUnauthenticatedAccessAuditService;
import com.brutecx.docflow_backend.web.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final IUnauthenticatedAccessAuditService unauthenticatedAccessAuditService;
    private final ClientIpResolver clientIpResolver;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        // We only use this entry point for /api/** in SecurityConfig, but keep it defensive.
        String uri = request.getRequestURI();

        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader("X-Correlation-Id"); // CHANGED
        }

        String httpMethod = request.getMethod();
        String ip = clientIpResolver.resolve(request);
        String userAgent = request.getHeader("User-Agent");

        String eventFingerprint = EventFingerprint.of(List.of(
                "UNAUTHENTICATED",
                correlationId,
                httpMethod,
                uri
        ));

        try {
            unauthenticatedAccessAuditService.record(
                    correlationId,
                    httpMethod,
                    uri,
                    ip,
                    userAgent,
                    eventFingerprint
            );
        } catch (Exception e) {
            // Never break auth flow due to audit persistence failure
            log.error(
                    "UNAUTH AUDIT FAILURE → correlationId={} method={} uri={}",
                    correlationId,
                    httpMethod,
                    uri,
                    e
            );
        }

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "UNAUTHORIZED",
                "Authentication required",
                uri
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
