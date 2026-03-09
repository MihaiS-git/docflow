package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.argument.StructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String SCHEMA_VERSION = "docflow_siem_v1";
    private static final String STREAM = "USER_SERVICE";

    private final UserRepository userRepository;
    private final AuditRequestContextExtractor contextExtractor;

    public User getRequiredCurrentUser() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            emitSecurityEvent(
                    false,
                    "user_current_resolve_unauthenticated",
                    "No authenticated user in security context",
                    kv("execution.context", resolveExecutionContext()),
                    kv("principal.type", "none")
            );
            throw new IllegalStateException("No authenticated user in security context");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof OidcUser oidcUser)) {
            emitSecurityEvent(
                    false,
                    "user_current_principal_unexpected",
                    "Authenticated principal is not an OIDC user",
                    kv("execution.context", resolveExecutionContext()),
                    kv("principal.type", principal == null ? "null" : principal.getClass().getName())
            );

            throw new IllegalStateException("Authenticated principal is not an OIDC user");
        }

        String externalSubjectId = oidcUser.getSubject();

        return userRepository.findByExternalSubjectId(externalSubjectId)
                .orElseThrow(() -> {
                    emitSecurityEvent(
                            false,
                            "user_subject_unmapped",
                            "Authenticated subject not mapped to local user",
                            kv("execution.context", resolveExecutionContext()),
                            kv("subject.id", externalSubjectId)
                    );
                    return new IllegalStateException(
                            "Authenticated subject not mapped to local user: " + externalSubjectId
                    );
                });
    }

    public User getRequired(UUID userId) {
        return userRepository.getRequired(userId);
    }

    public CurrentUserResult resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("No authenticated user");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof OidcUser oidcUser)) {
            throw new IllegalStateException("Authenticated principal is not OIDC");
        }

        String subject = oidcUser.getSubject();

        return userRepository.findByExternalSubjectId(subject)
                .map(user -> switch (user.getStatus()) {
                    case ACTIVE -> new CurrentUserResult(CurrentUserState.ACTIVE, user);
                    case LOCKED -> new CurrentUserResult(CurrentUserState.LOCKED, null);
                    case DISABLED -> new CurrentUserResult(CurrentUserState.DISABLED, null);
                })
                .orElseGet(() -> new CurrentUserResult(CurrentUserState.BOOTSTRAP, null));
    }

    @Transactional
    public int deleteUnactivatedInvitedUsers(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return 0;

        List<User> users = userRepository.findByStatusAndIdIn(UserStatus.LOCKED, userIds);

        int deleted = 0;
        for (User user : users) {
            if (user.getExternalSubjectId() != null) continue;
            userRepository.delete(user);
            deleted++;
        }

        emitSecurityEvent(
                true,
                "invited_users_purged",
                "Purged unactivated invited users",
                kv("execution.context", resolveExecutionContext()),
                kv("candidate.count", userIds.size()),
                kv("matched.locked.count", users.size()),
                kv("deleted.count", deleted),
                kv("actor.name", resolveActorName()),
                kv("actor.roles", resolveActorRoles())
        );

        return deleted;
    }

    private void emitSecurityEvent(
            boolean ok,
            String eventAction,
            String message,
            StructuredArgument... extra
    ) {
        List<Object> args = new ArrayList<>(24);

        var ctx = contextExtractor.fromCurrentRequest();
        String correlationId = ctx.correlationId();

        args.add(kv("schema_version", SCHEMA_VERSION));
        args.add(kv("event.category", "security"));
        args.add(kv("event.type", "identity"));
        args.add(kv("event.action", eventAction));
        args.add(kv("event.outcome", ok ? "success" : "failure"));
        args.add(kv("audit.stream", STREAM));
        args.add(kv("correlation.id", correlationId));
        args.add(kv("message", message));

        if (extra != null) {
            for (StructuredArgument a : extra) {
                if (a != null) args.add(a);
            }
        }

        if (ok) log.info("security_event {}", args.toArray());
        else log.warn("security_event {}", args.toArray());
    }

    private String resolveExecutionContext() {
        var ctx = contextExtractor.fromCurrentRequest();
        String corr = ctx.correlationId();
        return (corr != null && !corr.isBlank())
                ? ExecutionContext.HTTP.name()
                : ExecutionContext.SYSTEM.name();
    }

    private static String resolveActorName() {
        Authentication auth = SecurityContextHolder.getContext() != null
                ? SecurityContextHolder.getContext().getAuthentication()
                : null;

        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return "anonymous";
        }

        String name = auth.getName();
        return (name == null || name.isBlank()) ? "unknown" : name;
    }

    private static List<String> resolveActorRoles() {
        Authentication auth = SecurityContextHolder.getContext() != null
                ? SecurityContextHolder.getContext().getAuthentication()
                : null;

        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return List.of();
        }

        List<String> roles = new ArrayList<>();
        if (auth.getAuthorities() != null) {
            auth.getAuthorities().forEach(a -> {
                if (a != null && a.getAuthority() != null && !a.getAuthority().isBlank()) {
                    roles.add(a.getAuthority());
                }
            });
        }
        return roles;
    }
}