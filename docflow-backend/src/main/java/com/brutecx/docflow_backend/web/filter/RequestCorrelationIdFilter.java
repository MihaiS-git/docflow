package com.brutecx.docflow_backend.web.filter;

import com.brutecx.docflow_backend.logging.InfraEventActions;
import com.brutecx.docflow_backend.logging.InfraEventLogger;
import com.brutecx.docflow_backend.logging.InfraEventOutcome;
import com.brutecx.docflow_backend.logging.InfraEventType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestCorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "correlation.id";
    public static final String MDC_SOURCE_KEY = "correlation.source";

    private static final int MAX_LENGTH = 128;
    private static final Pattern SAFE_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._\\-:]+$");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String headerValue = request.getHeader(HEADER_NAME);

        String correlationId;
        String source;

        if (isValidHeader(headerValue)) {
            correlationId = headerValue.trim();
            source = "REQUEST_ID";
        } else {
            if (headerValue != null && !headerValue.isBlank()) {
                // Log rejection only when header was present but invalid
                InfraEventLogger.log(
                        InfraEventType.AUTHENTICATION,
                        InfraEventActions.AUTHN_CORRELATION_ID_REJECTED,
                        InfraEventOutcome.BLOCKED,
                        "Invalid inbound correlation id rejected",
                        null
                );
            }

            correlationId = UUID.randomUUID().toString();
            source = "GENERATED";
        }

        MDC.put(MDC_KEY, correlationId);
        MDC.put(MDC_SOURCE_KEY, source);

        response.setHeader(HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(MDC_SOURCE_KEY);
        }
    }

    private boolean isValidHeader(String value) {
        if (value == null) {
            return false;
        }

        String trimmed = value.trim();

        if (trimmed.isEmpty()) {
            return false;
        }

        if (trimmed.length() > MAX_LENGTH) {
            return false;
        }

        return SAFE_PATTERN.matcher(trimmed).matches();
    }
}