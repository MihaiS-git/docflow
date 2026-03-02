package com.brutecx.docflow_backend.infrastructure.keycloak;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KeycloakClientMetrics {

    private static final String REQUESTS = "docflow_keycloak_requests_total";
    private static final String FAILURES = "docflow_keycloak_request_failures_total";
    private static final String LATENCY = "docflow_keycloak_request_seconds";

    private final MeterRegistry registry;

    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    public KeycloakClientMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void incrementSuccess(String operation) {
        successCounters.computeIfAbsent(operation,
                op -> Counter.builder(REQUESTS)
                        .tag("operation", safe(op))
                        .tag("outcome", "success")
                        .register(registry)
        ).increment();
    }

    public void incrementFailure(String operation) {
        failureCounters.computeIfAbsent(operation,
                op -> Counter.builder(FAILURES)
                        .tag("operation", safe(op))
                        .register(registry)
        ).increment();
    }

    public void recordLatency(String operation, Duration duration) {
        if (duration == null || duration.isNegative()) return;

        latencyTimers.computeIfAbsent(operation,
                op -> Timer.builder(LATENCY)
                        .tag("operation", safe(op))
                        .publishPercentileHistogram()
                        .register(registry)
        ).record(duration);
    }

    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "UNKNOWN" : v;
    }
}