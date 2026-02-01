package com.brutecx.docflow_backend.audit;

import com.brutecx.docflow_backend.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public final class AuditRequestContextExtractor {

    private static final String REQUEST_ID_MDC_KEY = "requestId";

    private final ClientIpResolver clientIpResolver;

    public AuditRequestContextExtractor(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    public AuditRequestContext from(HttpServletRequest request) {
        String requestId = MDC.get(REQUEST_ID_MDC_KEY);
        String ip = clientIpResolver.resolve(request);
        String ua = request.getHeader("User-Agent");

        return new AuditRequestContext(
                requestId,
                null,
                ip != null ? ip : "UNKNOWN",
                ua != null ? ua : "N/A"
        );
    }

    /**
     * Extract audit context from the current HTTP request without passing HttpServletRequest around.
     * Works only in request threads.
     */
    public AuditRequestContext fromCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs == null) {
            // Called outside HTTP request context (scheduled jobs / async thread)
            return new AuditRequestContext(
                    MDC.get(REQUEST_ID_MDC_KEY),
                    null,
                    "N/A",
                    "N/A"
            );
        }

        return from(attrs.getRequest());
    }
}
