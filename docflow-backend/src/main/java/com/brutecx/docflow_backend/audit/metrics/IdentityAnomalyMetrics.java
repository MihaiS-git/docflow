package com.brutecx.docflow_backend.audit.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdentityAnomalyMetrics {

    private final MeterRegistry registry;

    public void incrementBindFailure(String tenantId) {
        registry.counter(
                "identity.bind.failure",
                "tenantId", tenantId != null ? tenantId : "UNKNOWN"
        ).increment();
    }
}