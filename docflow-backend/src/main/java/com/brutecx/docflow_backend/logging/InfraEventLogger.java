package com.brutecx.docflow_backend.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

public final class InfraEventLogger {

    public static final String LOGGER_NAME = "INFRA_EVENT";
    public static final String SCHEMA_VERSION = "docflow_siem_v1";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private InfraEventLogger() {
    }

    /* =====================================================
       BASE METHOD (backward compatible)
       ===================================================== */

    public static void log(
            InfraEventType type,
            String action,
            InfraEventOutcome outcome,
            String reason,
            Exception ex
    ) {
        log(type, action, outcome, reason, ex, null, (Object[]) null);
    }

    /* =====================================================
       EXTENDED METHOD (structured enrichment)
       ===================================================== */

    public static void log(
            InfraEventType type,
            String action,
            InfraEventOutcome outcome,
            String reason,
            Exception ex,
            Object... additionalKv
    ) {
        log(type, action, outcome, reason, ex, null, additionalKv);
    }

    /* =====================================================
       FULL METHOD (explicit correlation support)
       ===================================================== */

    public static void log(
            InfraEventType type,
            String action,
            InfraEventOutcome outcome,
            String reason,
            Exception ex,
            String correlationId,
            Object... additionalKv
    ) {

        List<Object> args = new ArrayList<>(20);

        InfraSeverity effectiveSeverity = resolveSeverity(type, outcome);

        args.add(kv("schema_version", SCHEMA_VERSION));
        args.add(kv("event.category", type.category()));
        args.add(kv("event.type", type.value()));
        args.add(kv("event.action", action));
        args.add(kv("event.outcome", outcome.value()));
        args.add(kv("event.severity", effectiveSeverity.name().toLowerCase()));
        args.add(kv("compliance.class", type.complianceClass().name().toLowerCase()));

        if (correlationId != null && !correlationId.isBlank()) {
            args.add(kv("correlation.id", correlationId));
        }

        if (reason != null && !reason.isBlank()) {
            args.add(kv("reason", reason));
        }

        if (ex != null) {
            args.add(kv("exception.class", ex.getClass().getSimpleName()));
        }

        if (additionalKv != null) {
            for (Object kvArg : additionalKv) {
                if (kvArg != null) {
                    args.add(kvArg);
                }
            }
        }

        switch (outcome) {
            case FAILURE -> log.error("infra_event {}", args.toArray(), ex);
            case BLOCKED -> log.warn("infra_event {}", args.toArray());
            default -> log.info("infra_event {}", args.toArray());
        }
    }

    /* =====================================================
       SEVERITY RESOLUTION
       ===================================================== */

    private static InfraSeverity resolveSeverity(
            InfraEventType type,
            InfraEventOutcome outcome
    ) {
        return switch (outcome) {
            case FAILURE -> InfraSeverity.ERROR;
            case BLOCKED -> InfraSeverity.WARN;
            default -> type.defaultSeverity();
        };
    }

    /* =====================================================
       SHORTCUTS
       ===================================================== */

    public static void failure(
            InfraEventType type,
            String action,
            String reason,
            Exception ex
    ) {
        log(type, action, InfraEventOutcome.FAILURE, reason, ex);
    }

    public static void blocked(
            InfraEventType type,
            String action,
            String reason
    ) {
        log(type, action, InfraEventOutcome.BLOCKED, reason, null);
    }

    public static void success(
            InfraEventType type,
            String action
    ) {
        log(type, action, InfraEventOutcome.SUCCESS, null, null);
    }
}