package com.brutecx.docflow_backend.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

public final class SecurityAuditLogger {

    public static final String LOGGER_NAME = "SECURITY_AUDIT";
    public static final String SCHEMA_VERSION = "docflow_siem_v1";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private SecurityAuditLogger() {
    }

    public static void auditFailure(
            String eventAction,
            String auditStream,
            String auditPartition,
            UUID tenantId,
            Exception ex,
            String correlationId
    ) {

        Actor actor = resolveActor();

        List<Object> args = new ArrayList<>(16);

        args.add(kv("schema_version", SCHEMA_VERSION));
        args.add(kv("event.category", "security"));
        args.add(kv("event.type", "audit"));
        args.add(kv("event.action", eventAction));
        args.add(kv("event.outcome", "failure"));

        if (correlationId != null && !correlationId.isBlank()) {
            args.add(kv("correlation.id", correlationId));
        }

        args.add(kv("audit.stream", auditStream));
        args.add(kv("audit.partition", auditPartition));

        if (tenantId != null) {
            args.add(kv("tenant.id", tenantId));
        }

        args.add(kv("actor.type", actor.type()));
        args.add(kv("actor.name", actor.name()));
        args.add(kv("actor.roles", actor.roles()));

        if (ex != null) {
            args.add(kv("exception.class", ex.getClass().getSimpleName()));
        }

        log.error("security_event {}", args.toArray(), ex);
    }

    private static Actor resolveActor() {

        Authentication auth = SecurityContextHolder.getContext() != null
                ? SecurityContextHolder.getContext().getAuthentication()
                : null;

        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return new Actor("ANONYMOUS", "anonymous", List.of());
        }

        String name = auth.getName() != null ? auth.getName() : "unknown";

        List<String> roles = new ArrayList<>();

        if (auth.getAuthorities() != null) {
            auth.getAuthorities().forEach(a -> {
                if (a != null && a.getAuthority() != null && !a.getAuthority().isBlank()) {
                    roles.add(a.getAuthority());
                }
            });
        }

        return new Actor("USER", name, roles);
    }

    private record Actor(String type, String name, List<String> roles) {
    }
}