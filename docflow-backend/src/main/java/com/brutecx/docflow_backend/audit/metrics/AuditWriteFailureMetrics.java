package com.brutecx.docflow_backend.audit.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuditWriteFailureMetrics {

    private static final String FAILURES = "docflow_audit_write_failures_total";
    private static final String SUCCESSES = "docflow_audit_write_success_total";
    private static final String DEDUP = "docflow_audit_write_dedup_total";
    private static final String LATENCY = "docflow_audit_write_seconds";

    private final MeterRegistry registry;

    // Cache metrics per (stream + execution_context)
    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> dedupCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    public AuditWriteFailureMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void incrementFailure(String stream, String executionContext, Exception ex) {
        String reason = classifyReason(ex);

        String key = key(stream, executionContext, reason);

        failureCounters.computeIfAbsent(key, k ->
                Counter.builder(FAILURES)
                        .tag("stream", safe(stream))
                        .tag("execution_context", safe(executionContext))
                        .tag("reason", safe(reason))
                        .register(registry)
        ).increment();
    }

    public void incrementSuccess(String stream, String executionContext) {
        String key = key(stream, executionContext);

        successCounters.computeIfAbsent(key, k ->
                Counter.builder(SUCCESSES)
                        .tag("stream", safe(stream))
                        .tag("execution_context", safe(executionContext))
                        .register(registry)
        ).increment();
    }

    public void incrementDedup(String stream, String executionContext) {
        String key = key(stream, executionContext);

        dedupCounters.computeIfAbsent(key, k ->
                Counter.builder(DEDUP)
                        .tag("stream", safe(stream))
                        .tag("execution_context", safe(executionContext))
                        .register(registry)
        ).increment();
    }

    public void recordLatency(String stream, String executionContext, Duration duration) {
        if (duration == null || duration.isNegative()) return;

        String key = key(stream, executionContext);

        latencyTimers.computeIfAbsent(key, k ->
                Timer.builder(LATENCY)
                        .tag("stream", safe(stream))
                        .tag("execution_context", safe(executionContext))
                        .publishPercentileHistogram()
                        .register(registry)
        ).record(duration);
    }

    private static String classifyReason(Exception ex) {
        if (ex == null) return "OTHER";
        if (ex instanceof DataIntegrityViolationException) return "DATA_INTEGRITY";
        if (ex instanceof QueryTimeoutException) return "TIMEOUT";
        if (ex instanceof TransientDataAccessException) return "DB_TRANSIENT";

        Throwable t = ex;
        while (t != null) {
            if (t instanceof SQLException) return "DB";
            t = t.getCause();
        }

        String simple = ex.getClass().getSimpleName();
        if (simple != null) {
            String s = simple.toLowerCase();
            if (s.contains("timeout")) return "TIMEOUT";
            if (s.contains("serialize") || s.contains("json") || s.contains("jackson"))
                return "SERIALIZATION";
            if (s.contains("signature") || s.contains("crypto") || s.contains("hash"))
                return "CHAIN";
            if (s.contains("rest") || s.contains("client"))
                return "UPSTREAM";
        }

        return "OTHER";
    }

    private static String key(String stream, String executionContext) {
        return safe(stream) + "|" + safe(executionContext);
    }

    private static String key(String stream, String executionContext, String reason) {
        return safe(stream) + "|" + safe(executionContext) + "|" + safe(reason);
    }

    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "UNKNOWN" : v;
    }
}