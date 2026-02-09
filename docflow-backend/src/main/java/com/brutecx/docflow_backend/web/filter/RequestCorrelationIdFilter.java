package com.brutecx.docflow_backend.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * A filter that assigns a unique correlation ID to each incoming HTTP request.
 * The correlation ID is retrieved from the "X-Request-Id" header if present;
 * otherwise, a new UUID is generated. The correlation ID is stored in the MDC
 * for logging purposes and added to the response headers.
 */
@Component
public class RequestCorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String MDC_SOURCE_KEY = "correlationSource";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        boolean fromHeader = Optional.ofNullable(request.getHeader(HEADER_NAME))
                 .filter(h -> !h.isBlank())
                 .isPresent();

         String requestId = fromHeader
                 ? request.getHeader(HEADER_NAME)
                 : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, requestId);
        MDC.put(MDC_SOURCE_KEY, fromHeader ? "REQUEST_ID" : "GENERATED");
        response.setHeader(HEADER_NAME, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(MDC_SOURCE_KEY);
        }
    }
}
