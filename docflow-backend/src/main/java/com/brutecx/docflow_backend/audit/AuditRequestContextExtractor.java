package com.brutecx.docflow_backend.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Extracts {@link AuditRequestContext} for the current execution.
 *
 * In HTTP request threads the context is built once by
 * {@link com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter}
 * and stored in the request attributes.
 *
 * In non-HTTP executions (scheduled jobs, async tasks, tests) a fallback
 * context is returned.
 */
@Component
public final class AuditRequestContextExtractor {

    /**
     * Request attribute containing the audit context.
     */
    public static final String AUDIT_CTX_ATTR =
            AuditRequestContext.class.getName() + ".CTX";

    private static final String UNKNOWN = "UNKNOWN";
    private static final String NA = "N/A";

    /**
     * Singleton fallback context used outside HTTP request scope.
     */
    private static final AuditRequestContext FALLBACK_CONTEXT =
            new AuditRequestContext(
                    UNKNOWN,
                    UNKNOWN,
                    NA,
                    NA,
                    UNKNOWN
            );

    /**
     * Extract audit context from the current HTTP request.
     * Works in request threads and background executions.
     */
    public AuditRequestContext fromCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs == null) {
            return FALLBACK_CONTEXT;
        }

        HttpServletRequest request = attrs.getRequest();
        Object cached = request.getAttribute(AUDIT_CTX_ATTR);

        if (cached instanceof AuditRequestContext ctx) {
            return ctx;
        }

        return FALLBACK_CONTEXT;
    }

    /**
     * Extract audit context directly from a provided request.
     */
    public AuditRequestContext from(HttpServletRequest request) {
        if (request == null) {
            return FALLBACK_CONTEXT;
        }

        Object cached = request.getAttribute(AUDIT_CTX_ATTR);

        if (cached instanceof AuditRequestContext ctx) {
            return ctx;
        }

        return FALLBACK_CONTEXT;
    }
}