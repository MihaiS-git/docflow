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

    /** Distributed tracing id (may come from client or proxy) */
    public static final String MDC_CORRELATION_ID = "correlation.id";

    /** Indicates if correlation id was client provided or generated */
    public static final String MDC_CORRELATION_SOURCE = "correlation.source";

    /** Guaranteed server-generated request id (unique per request) */
    public static final String MDC_REQUEST_ID = "request.id";

    /** HTTP request path used by audit subsystem */
    public static final String MDC_REQUEST_PATH = "request.path";

    /** Client IP address */
    public static final String MDC_CLIENT_IP = "client.ip";

    /** Client user agent */
    public static final String MDC_USER_AGENT = "client.user_agent";

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
        String correlationSource;

        if (isValidHeader(headerValue)) {
            correlationId = headerValue.trim();
            correlationSource = "REQUEST_ID";
        } else {
            if (headerValue != null && !headerValue.isBlank()) {
                InfraEventLogger.log(
                        InfraEventType.AUTHENTICATION,
                        InfraEventActions.AUTHN_CORRELATION_ID_REJECTED,
                        InfraEventOutcome.BLOCKED,
                        "Invalid inbound correlation id rejected",
                        null
                );
            }

            correlationId = UUID.randomUUID().toString();
            correlationSource = "GENERATED";
        }

        /*
         * Server request id — guaranteed unique per request.
         */
        String requestId = UUID.randomUUID().toString();

        /*
         * HTTP resource path.
         */
        String requestPath = request.getRequestURI();

        /*
         * Client metadata
         */
        String userAgent = request.getHeader("User-Agent");
        String clientIp = request.getRemoteAddr();

        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_CORRELATION_SOURCE, correlationSource);
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_REQUEST_PATH, requestPath);
        MDC.put(MDC_CLIENT_IP, clientIp != null ? clientIp : "UNKNOWN");
        MDC.put(MDC_USER_AGENT, userAgent != null ? userAgent : "UNKNOWN");

        response.setHeader(HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_CORRELATION_SOURCE);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_REQUEST_PATH);
            MDC.remove(MDC_CLIENT_IP);
            MDC.remove(MDC_USER_AGENT);
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