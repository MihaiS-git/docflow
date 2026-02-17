package com.brutecx.docflow_backend.audit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class AuditWriteFailureMetrics {

    private static final String METRIC = "docflow_audit_write_failures_total";

    private final MeterRegistry registry;

    public AuditWriteFailureMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void increment(String stream, String executionContext, Exception ex) {
        String reason = classify(ex);

        Counter.builder(METRIC)
                .tag("stream", safe(stream))
                .tag("execution_context", safe(executionContext))
                .tag("reason", safe(reason))
                .register(registry)
                .increment();
    }

    private static String classify(Exception ex) {
        if (ex instanceof DataIntegrityViolationException) return "DATA_INTEGRITY";
        // keep small & stable; don’t explode cardinality
        String simple = ex.getClass().getSimpleName();
        if (simple == null || simple.isBlank()) return "UNKNOWN";
        return simple;
    }

    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "UNKNOWN" : v;
    }
}
