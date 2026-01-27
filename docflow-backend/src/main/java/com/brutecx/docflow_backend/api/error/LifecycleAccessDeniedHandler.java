package com.brutecx.docflow_backend.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LifecycleAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public LifecycleAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException ex
    ) throws IOException {

        ErrorResponse body = ErrorResponse.of(
                403,
                "Forbidden",
                "LIFECYCLE_ACCESS_DENIED",
                ex.getMessage(),
                request.getRequestURI()
        );

        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
