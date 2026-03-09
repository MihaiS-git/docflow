package com.brutecx.docflow_backend.audit;

import com.brutecx.docflow_backend.web.ClientIpResolver;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public final class AuditRequestContextExtractor {

    private final ClientIpResolver clientIpResolver;

    public AuditRequestContextExtractor(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    public AuditRequestContext from(HttpServletRequest request) {

        String correlationId = MDC.get(RequestCorrelationIdFilter.MDC_CORRELATION_ID);
        String requestId = MDC.get(RequestCorrelationIdFilter.MDC_REQUEST_ID);

        String ip = clientIpResolver.resolve(request);
        String ua = request.getHeader("User-Agent");

        String resourcePath = request.getRequestURI();

        return new AuditRequestContext(
                correlationId,
                requestId,
                ip != null ? ip : "UNKNOWN",
                ua != null ? ua : "N/A",
                resourcePath != null ? resourcePath : "UNKNOWN"
        );
    }

    /**
     * Extract audit context from the current HTTP request.
     * Works only in request threads.
     */
    public AuditRequestContext fromCurrentRequest() {

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        String correlationId = MDC.get(RequestCorrelationIdFilter.MDC_CORRELATION_ID);
        String requestId = MDC.get(RequestCorrelationIdFilter.MDC_REQUEST_ID);

        if (attrs == null) {

            String path = MDC.get(RequestCorrelationIdFilter.MDC_REQUEST_PATH);
            String ip = MDC.get(RequestCorrelationIdFilter.MDC_CLIENT_IP);
            String ua = MDC.get(RequestCorrelationIdFilter.MDC_USER_AGENT);

            return new AuditRequestContext(
                    correlationId != null ? correlationId : "UNKNOWN",
                    requestId != null ? requestId : "UNKNOWN",
                    ip != null ? ip : "N/A",
                    ua != null ? ua : "N/A",
                    path != null ? path : "UNKNOWN"
            );
        }

        return from(attrs.getRequest());
    }
}