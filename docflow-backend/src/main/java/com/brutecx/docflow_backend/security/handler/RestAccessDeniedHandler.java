package com.brutecx.docflow_backend.security.handler;

import com.brutecx.docflow_backend.api.error.ErrorResponse;
import com.brutecx.docflow_backend.api.error.LifecycleAccessDeniedException;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.lifecycle.ILifecycleDeniedAuditService;
import com.brutecx.docflow_backend.audit.rbac.IRbacDeniedAuditService;
import com.brutecx.docflow_backend.web.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
    private final ClientIpResolver clientIpResolver;
    private final IRbacDeniedAuditService rbacDeniedAuditService;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException ex
    ) throws IOException, ServletException {

        var auth = SecurityContextHolder.getContext().getAuthentication();

        log.error(
                "ACCESS DENIED → uri={}, authorities={}, exception={}",
                request.getRequestURI(),
                auth != null ? auth.getAuthorities() : "NO_AUTH",
                ex.getClass().getSimpleName()
        );

        String errorCode = "ACCESS_DENIED";

        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader("X-Correlation-Id");
        }

        String subjectId = null;
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidcUser) {
            subjectId = oidcUser.getSubject();
        }

        String ip = clientIpResolver.resolve(request);
        String userAgent = request.getHeader("User-Agent");
        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();

        if (ex instanceof LifecycleAccessDeniedException lifecycleEx) {
            errorCode = lifecycleEx.getErrorCode();

            String eventFingerprint = EventFingerprint.of(List.of(
                    "LIFECYCLE_DENIED",
                    errorCode,
                    uri,
                    correlationId
            ));

            try {
                lifecycleDeniedAuditService.record(
                        correlationId,
                        subjectId,
                        errorCode,
                        httpMethod,
                        correlationId,
                        ip,
                        userAgent,
                        eventFingerprint
                );
            } catch (Exception auditEx) {
                if (auditEx instanceof DataIntegrityViolationException) {
                    log.warn(
                            "Lifecycle audit deduped. correlationId={} reasonCode={} uri={}",
                            correlationId,
                            errorCode,
                            uri
                    );
                }

                Throwable root = auditEx;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }

                log.error(
                        "LIFECYCLE AUDIT FAILURE → correlationId={} subjectId={} reasonCode={} method={} uri={} rootType={} rootMsg={}",
                        correlationId,
                        subjectId,
                        errorCode,
                        httpMethod,
                        uri,
                        root.getClass().getName(),
                        root.getMessage(),
                        auditEx
                );
            }

            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            SecurityContextHolder.clearContext();

            // Explicitly expire JSESSIONID (Tomcat default cookie)
            response.addHeader(
                    "Set-Cookie",
                    "JSESSIONID=; Max-Age=0; Path=/; HttpOnly; SameSite=None; Secure"
            );
        } else {
            String eventFingerprint = EventFingerprint.of(List.of(
                    "RBAC_DENIED",
                    uri,
                    correlationId
            ));

            try {
                rbacDeniedAuditService.record(
                        correlationId,
                        subjectId,
                        request.getMethod(),
                        request.getRequestURI(),
                        clientIpResolver.resolve(request),
                        request.getHeader("User-Agent"),
                        eventFingerprint
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
                errorCode,
                ex.getMessage(),
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
