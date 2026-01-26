package com.brutecx.docflow_backend.security.handler;

import com.brutecx.docflow_backend.api.error.ErrorResponse;
import com.brutecx.docflow_backend.security.enforcement.LifecycleAccessDeniedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

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

        if (ex instanceof LifecycleAccessDeniedException lifecycleEx) {
            errorCode = lifecycleEx.getErrorCode();

            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            SecurityContextHolder.clearContext();

            // Explicitly expire JSESSIONID (Tomcat default cookie)
            response.addHeader(
                    "Set-Cookie",
                    "JSESSIONID=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax"
            );
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
