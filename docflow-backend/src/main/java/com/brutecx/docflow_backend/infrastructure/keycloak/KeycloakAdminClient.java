package com.brutecx.docflow_backend.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Client for interacting with Keycloak Admin API to fetch events and user details.
 * Uses client credentials to authenticate.
 *
 * @see KeycloakAdminPullProperties
 * @see KeycloakUser
 * @see KeycloakAdminEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

    public record RealmUserRoles(
            boolean identityExists,
            List<String> roles
    ) {}

    private static final String SCHEMA = "docflow_siem_v1";
    private static final String EXEC_CTX = "ADMIN_API";

    private static final String OP_FETCH_EVENTS = "fetch_events";
    private static final String OP_FETCH_USER = "fetch_user";
    private static final String OP_FETCH_TOKEN = "fetch_token";
    private static final String OP_ASSIGN_ROLE = "assign_role";
    private static final String OP_REVOKE_ROLE = "revoke_role";
    private static final String OP_FETCH_REALM_ROLE = "fetch_realm_role";
    private static final String OP_FETCH_ROLES_FOR_USERS = "fetch_roles_for_users";
    private static final String OP_SET_TEMP_PASSWORD = "set_temporary_password";
    private static final String OP_CREATE_USER_INVITE_ONLY = "create_user_invite_only";
    private static final String OP_UPDATE_REQUIRED_ACTIONS = "update_required_actions";
    private static final String OP_FIND_USER_BY_EMAIL = "find_user_by_email_or_username";
    private static final String OP_DELETE_USER = "delete_user";

    private final ObjectMapper objectMapper;
    private final KeycloakAdminPullProperties props;
    private final RestClient keycloakAdminRestClient;
    private final KeycloakClientMetrics metrics;

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<>() {
    };
    private static final String ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";
    private static final String ACTION_VERIFY_EMAIL = "VERIFY_EMAIL";

    public List<KeycloakAdminEvent> fetchEvents(long sinceTimeMs) {

        final long startNs = System.nanoTime();

        try {
            String token = fetchAccessToken();

            String url = props.baseUrl()
                    + "/admin/realms/" + props.realm()
                    + "/events"
                    + "?dateFrom=" + (sinceTimeMs > 0 ? sinceTimeMs : 0)
                    + "&max=" + props.pageSize();

            String body;
            try {
                body = keycloakAdminRestClient.get()
                        .uri(url)
                        .headers(h -> h.setBearerAuth(token))
                        .retrieve()
                        .body(String.class);
            } catch (Exception ex) {
                metrics.incrementFailure(OP_FETCH_EVENTS);
                log.error("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "keycloak_fetch_events"),
                        kv("event.outcome", "failure"),
                        kv("execution.context", EXEC_CTX),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );
                throw new RestClientException("Keycloak events fetch failed", ex);
            }

            if (body == null || body.isBlank()) {
                metrics.incrementFailure(OP_FETCH_EVENTS);
                throw new RestClientException("Keycloak events fetch failed: empty body");
            }

            try {
                List<KeycloakAdminEvent> result = objectMapper.readValue(body, new TypeReference<>() {
                });
                metrics.incrementSuccess(OP_FETCH_EVENTS);
                return result;
            } catch (Exception e) {
                metrics.incrementFailure(OP_FETCH_EVENTS);
                log.error("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "keycloak_fetch_events_parse"),
                        kv("event.outcome", "failure"),
                        kv("execution.context", EXEC_CTX),
                        kv("exception.class", e.getClass().getSimpleName()),
                        e
                );
                throw new RestClientException("Failed to parse Keycloak events", e);
            }

        } finally {
            metrics.recordLatency(OP_FETCH_EVENTS, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    public KeycloakUser fetchUser(String userId) {

        final long startNs = System.nanoTime();

        try {
            try {
                String token = fetchAccessToken();

                String url = props.baseUrl()
                        + "/admin/realms/" + props.realm()
                        + "/users/" + userId;

                String body = keycloakAdminRestClient.get()
                        .uri(url)
                        .headers(h -> h.setBearerAuth(token))
                        .retrieve()
                        .body(String.class);

                if (body == null || body.isBlank()) {
                    metrics.incrementSuccess(OP_FETCH_USER);
                    return null;
                }

                KeycloakUser user = objectMapper.readValue(body, KeycloakUser.class);
                metrics.incrementSuccess(OP_FETCH_USER);
                return user;

            } catch (RestClientException ex) {
                // Preserve original behavior: warn + return null.
                metrics.incrementFailure(OP_FETCH_USER);
                log.warn("Keycloak fetchUser failed for userId={}", userId, ex);
                return null; // user deleted or access revoked
            } catch (Exception ex) {
                metrics.incrementFailure(OP_FETCH_USER);
                throw new IllegalStateException("Failed to parse Keycloak user", ex);
            }
        } finally {
            metrics.recordLatency(OP_FETCH_USER, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    /* =========================
       TOKEN
       ========================= */
    private String fetchAccessToken() {

        final long startNs = System.nanoTime();

        String tokenUrl = props.baseUrl()
                + "/realms/" + props.realm()
                + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());

        TokenResponse tokenResponse;
        try {
            tokenResponse = keycloakAdminRestClient.post()
                    .uri(tokenUrl)
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (Exception ex) {
            metrics.incrementFailure(OP_FETCH_TOKEN);
            log.error("security_event",
                    kv("schema_version", SCHEMA),
                    kv("event.category", "identity"),
                    kv("event.action", "keycloak_fetch_token"),
                    kv("event.outcome", "failure"),
                    kv("execution.context", EXEC_CTX),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );
            throw new RestClientException("Keycloak token fetch failed", ex);
        } finally {
            metrics.recordLatency(OP_FETCH_TOKEN, Duration.ofNanos(System.nanoTime() - startNs));
        }

        if (tokenResponse == null || tokenResponse.accessToken == null || tokenResponse.accessToken.isBlank()) {
            metrics.incrementFailure(OP_FETCH_TOKEN);
            throw new RestClientException("Keycloak token fetch failed: empty token");
        }

        metrics.incrementSuccess(OP_FETCH_TOKEN);
        return tokenResponse.accessToken;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TokenResponse {
        @JsonProperty("access_token")
        public String accessToken;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakAdminEvent(
            @JsonProperty("time") long time,
            @JsonProperty("type") String type,
            @JsonProperty("realmId") String realmId,
            @JsonProperty("clientId") String clientId,
            @JsonProperty("userId") String userId,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("ipAddress") String ipAddress,
            @JsonProperty("error") String error,
            @JsonProperty("details") Map<String, String> details
    ) {
    }

    /**
     * Assigns the given realm role to the user with the given userId.
     */
    public void assignRealmRole(String userId, String roleName) {

        final long startNs = System.nanoTime();

        try {
            String token = fetchAccessToken();
            Map<String, Object> role = fetchRealmRole(roleName, token);

            String url = props.baseUrl()
                    + "/admin/realms/" + props.realm()
                    + "/users/" + userId
                    + "/role-mappings/realm";

            try {
                keycloakAdminRestClient.post()
                        .uri(url)
                        .headers(h -> h.setBearerAuth(token))
                        .body(List.of(role))
                        .retrieve()
                        .toBodilessEntity();

                metrics.incrementSuccess(OP_ASSIGN_ROLE);

                log.info("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "assign_realm_role"),
                        kv("event.outcome", "success"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", userId),
                        kv("role.name", roleName)
                );

            } catch (Exception ex) {
                metrics.incrementFailure(OP_ASSIGN_ROLE);

                log.error("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "assign_realm_role"),
                        kv("event.outcome", "failure"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", userId),
                        kv("role.name", roleName),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );

                // Preserve original behavior (error log + throw RestClientException)
                log.error(
                        "KEYCLOAK ROLE ASSIGN FAILED role={} userId={}",
                        roleName, userId, ex
                );

                throw new RestClientException(
                        "Failed to assign realm role '" + roleName + "' to user " + userId,
                        ex
                );
            }

        } finally {
            metrics.recordLatency(OP_ASSIGN_ROLE, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    public void revokeRealmRole(String userId, String roleName) {

        final long startNs = System.nanoTime();

        try {
            String token = fetchAccessToken();

            Map<String, Object> role = fetchRealmRole(roleName, token);

            String url = props.baseUrl()
                    + "/admin/realms/" + props.realm()
                    + "/users/" + userId
                    + "/role-mappings/realm";

            try {
                keycloakAdminRestClient.method(HttpMethod.DELETE)
                        .uri(url)
                        .headers(h -> h.setBearerAuth(token))
                        .body(List.of(role))
                        .retrieve()
                        .toBodilessEntity();

                metrics.incrementSuccess(OP_REVOKE_ROLE);

                log.info("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "revoke_realm_role"),
                        kv("event.outcome", "success"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", userId),
                        kv("role.name", roleName)
                );

            } catch (Exception ex) {
                metrics.incrementFailure(OP_REVOKE_ROLE);

                log.error("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "revoke_realm_role"),
                        kv("event.outcome", "failure"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", userId),
                        kv("role.name", roleName),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );

                throw new RestClientException(
                        "Failed to revoke realm role '" + roleName + "' from user " + userId,
                        ex
                );
            }

        } finally {
            metrics.recordLatency(OP_REVOKE_ROLE, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    private Map<String, Object> fetchRealmRole(String roleName, String token) {

        final long startNs = System.nanoTime();

        String url = props.baseUrl()
                + "/admin/realms/" + props.realm()
                + "/roles/" + roleName;

        try {
            String body = keycloakAdminRestClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                metrics.incrementFailure(OP_FETCH_REALM_ROLE);
                throw new RestClientException(
                        "Failed to fetch realm role '" + roleName + "': empty response"
                );
            }

            Map<String, Object> role = objectMapper.readValue(
                    body,
                    new TypeReference<Map<String, Object>>() {
                    }
            );

            metrics.incrementSuccess(OP_FETCH_REALM_ROLE);
            return role;

        } catch (Exception ex) {
            metrics.incrementFailure(OP_FETCH_REALM_ROLE);
            throw new RestClientException(
                    "Failed to fetch realm role '" + roleName + "'",
                    ex
            );
        } finally {
            metrics.recordLatency(OP_FETCH_REALM_ROLE, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    public Map<String, RealmUserRoles> fetchRealmRolesForUsers(List<String> userIds) {

        final long startNs = System.nanoTime();

        try {
            if (userIds == null || userIds.isEmpty()) {
                metrics.incrementSuccess(OP_FETCH_ROLES_FOR_USERS);
                return Map.of();
            }

            String token = fetchAccessToken();

            Map<String, RealmUserRoles> result = new HashMap<>();

            for (String userId : userIds) {

                if (userId == null || userId.isBlank()) {
                    continue;
                }

                String url = props.baseUrl()
                        + "/admin/realms/" + props.realm()
                        + "/users/" + userId
                        + "/role-mappings/realm/composite";

                try {

                    String body = keycloakAdminRestClient.get()
                            .uri(url)
                            .headers(h -> h.setBearerAuth(token))
                            .retrieve()
                            .body(String.class);

                    if (body == null || body.isBlank()) {
                        result.put(userId, new RealmUserRoles(true, List.of()));
                        continue;
                    }

                    List<Map<String, Object>> roles =
                            objectMapper.readValue(body, LIST_OF_MAP);

                    List<String> roleNames =
                            roles.stream()
                                    .map(r -> (String) r.get("name"))
                                    .filter(Objects::nonNull)
                                    .toList();

                    result.put(userId, new RealmUserRoles(true, roleNames));

                } catch (HttpClientErrorException.NotFound ex) {

                    log.warn(
                            "Keycloak user missing while fetching roles: {}",
                            userId
                    );

                    result.put(userId, new RealmUserRoles(false, List.of()));

                } catch (Exception ex) {

                    metrics.incrementFailure(OP_FETCH_ROLES_FOR_USERS);

                    throw new RestClientException(
                            "Failed to fetch realm roles for user " + userId,
                            ex
                    );
                }
            }

            metrics.incrementSuccess(OP_FETCH_ROLES_FOR_USERS);
            return result;

        } finally {
            metrics.recordLatency(OP_FETCH_ROLES_FOR_USERS,
                    Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    /**
     * Invite-only onboarding:
     * - ensure user exists
     * - merge required actions
     * - set TEMPORARY password (bootstrap credential)
     */
    public String ensureInviteUserExistsWithRequiredActionsAndTempPassword(
            String email,
            String temporaryPassword
    ) {
        EnsureUserResult r = ensureInviteUserExists(email);
        String userId = r.userId();
        setTemporaryPassword(userId, temporaryPassword);

        updateRequiredActions(userId, List.of(
                ACTION_UPDATE_PASSWORD,
                ACTION_VERIFY_EMAIL
        ));

        // New invited user must have global USER role.
        if (r.createdNew()) {
            try {
                assignRealmRole(userId, "USER");
            } catch (Exception ex) {
                // Fail hard: role must be present.
                throw ex;
            }
        }

        return userId;
    }

    // Admin API reset-password call
    private void setTemporaryPassword(String userId, String password) {

        final long startNs = System.nanoTime();

        try {
            String token = fetchAccessToken();
            String url = adminBaseUrl() + "/users/" + userId + "/reset-password";

            Map<String, Object> payload = Map.of(
                    "type", "password",
                    "value", password,
                    "temporary", true
            );

            try {
                keycloakAdminRestClient.put()
                        .uri(url)
                        .headers(h -> h.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();

                metrics.incrementSuccess(OP_SET_TEMP_PASSWORD);

                log.info("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "set_temporary_password"),
                        kv("event.outcome", "success"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", userId)
                );

            } catch (Exception ex) {
                metrics.incrementFailure(OP_SET_TEMP_PASSWORD);

                log.error("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "set_temporary_password"),
                        kv("event.outcome", "failure"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", userId),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );

                throw new RestClientException(
                        "Failed to set temporary password for user " + userId,
                        ex
                );
            }

        } finally {
            metrics.recordLatency(OP_SET_TEMP_PASSWORD, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    private EnsureUserResult ensureInviteUserExists(String email) {
        String userId = findUserIdByEmailOrUsername(email);
        if (userId != null) {
            return new EnsureUserResult(userId, false);
        }
        return new EnsureUserResult(createUserInviteOnly(email), true);
    }

    private record EnsureUserResult(String userId, boolean createdNew) {
    }

    private String findUserIdByEmailOrUsername(String email) {

        final long startNs = System.nanoTime();

        try {
            String token = fetchAccessToken();
            String url = adminBaseUrl() + "/users?search=" + urlEncode(email);

            String body = keycloakAdminRestClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                metrics.incrementSuccess(OP_FIND_USER_BY_EMAIL);
                return null;
            }

            List<Map<String, Object>> users;
            try {
                users = objectMapper.readValue(body, LIST_OF_MAP);
            } catch (Exception ex) {
                metrics.incrementFailure(OP_FIND_USER_BY_EMAIL);
                throw new RestClientException("Keycloak user search parse failed", ex);
            }

            for (Map<String, Object> u : users) {
                if (email.equalsIgnoreCase(String.valueOf(u.get("email")))
                        || email.equalsIgnoreCase(String.valueOf(u.get("username")))) {
                    metrics.incrementSuccess(OP_FIND_USER_BY_EMAIL);
                    return String.valueOf(u.get("id"));
                }
            }

            metrics.incrementSuccess(OP_FIND_USER_BY_EMAIL);
            return null;

        } finally {
            metrics.recordLatency(OP_FIND_USER_BY_EMAIL, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    private String createUserInviteOnly(String email) {

        final long startNs = System.nanoTime();

        try {
            String token = fetchAccessToken();
            String url = adminBaseUrl() + "/users";

            Map<String, Object> payload = Map.of(
                    "username", email.split("@")[0],
                    "email", email,
                    "enabled", true,
                    "emailVerified", false
            );

            ResponseEntity<Void> resp = keycloakAdminRestClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            String location = resp.getHeaders().getFirst(HttpHeaders.LOCATION);
            String idFromLocation = extractUserIdFromLocation(location);
            if (idFromLocation != null) {
                metrics.incrementSuccess(OP_CREATE_USER_INVITE_ONLY);

                log.info("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "create_user_invite_only"),
                        kv("event.outcome", "success"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", idFromLocation)
                );

                return idFromLocation;
            }

            String fallback = findUserIdByEmailOrUsername(email);
            if (fallback == null) {
                metrics.incrementFailure(OP_CREATE_USER_INVITE_ONLY);
                throw new IllegalStateException("Keycloak user created but id not resolvable");
            }

            metrics.incrementSuccess(OP_CREATE_USER_INVITE_ONLY);

            log.info("security_event",
                    kv("schema_version", SCHEMA),
                    kv("event.category", "identity"),
                    kv("event.action", "create_user_invite_only"),
                    kv("event.outcome", "success"),
                    kv("execution.context", EXEC_CTX),
                    kv("subject.id", fallback)
            );

            return fallback;

        } catch (RuntimeException ex) {
            metrics.incrementFailure(OP_CREATE_USER_INVITE_ONLY);

            log.error("security_event",
                    kv("schema_version", SCHEMA),
                    kv("event.category", "identity"),
                    kv("event.action", "create_user_invite_only"),
                    kv("event.outcome", "failure"),
                    kv("execution.context", EXEC_CTX),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );

            throw ex;

        } finally {
            metrics.recordLatency(OP_CREATE_USER_INVITE_ONLY, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    private void updateRequiredActions(String userId, List<String> actions) {

        final long startNs = System.nanoTime();

        try {
            String token = fetchAccessToken();
            String url = adminBaseUrl() + "/users/" + userId;

            try {
                keycloakAdminRestClient.put()
                        .uri(url)
                        .headers(h -> h.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("requiredActions", actions))
                        .retrieve()
                        .toBodilessEntity();

                metrics.incrementSuccess(OP_UPDATE_REQUIRED_ACTIONS);

                log.info("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "update_required_actions"),
                        kv("event.outcome", "success"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", userId)
                );

            } catch (Exception ex) {
                metrics.incrementFailure(OP_UPDATE_REQUIRED_ACTIONS);

                log.error("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "update_required_actions"),
                        kv("event.outcome", "failure"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", userId),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );

                throw new RestClientException("Failed to update requiredActions", ex);
            }

        } finally {
            metrics.recordLatency(OP_UPDATE_REQUIRED_ACTIONS, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    private String adminBaseUrl() {
        return props.baseUrl() + "/admin/realms/" + props.realm();
    }

    private static String urlEncode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String extractUserIdFromLocation(String location) {
        if (location == null || location.isBlank()) return null;
        int idx = location.lastIndexOf('/');
        if (idx < 0 || idx == location.length() - 1) return null;
        return location.substring(idx + 1);
    }

    public void deleteUserById(String subjectId) {

        final long startNs = System.nanoTime();

        Objects.requireNonNull(subjectId, "subjectId");

        try {
            String token = fetchAccessToken();
            String url = adminBaseUrl() + "/users/" + subjectId;

            try {
                keycloakAdminRestClient.delete()
                        .uri(url)
                        .headers(h -> h.setBearerAuth(token))
                        .retrieve()
                        .toBodilessEntity();

                metrics.incrementSuccess(OP_DELETE_USER);

                log.info("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "delete_user"),
                        kv("event.outcome", "success"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", subjectId)
                );

            } catch (Exception ex) {
                metrics.incrementFailure(OP_DELETE_USER);

                log.error("security_event",
                        kv("schema_version", SCHEMA),
                        kv("event.category", "identity"),
                        kv("event.action", "delete_user"),
                        kv("event.outcome", "failure"),
                        kv("execution.context", EXEC_CTX),
                        kv("subject.id", subjectId),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );

                throw new RestClientException(
                        "Failed to delete Keycloak user " + subjectId,
                        ex
                );
            }

        } finally {
            metrics.recordLatency(OP_DELETE_USER, Duration.ofNanos(System.nanoTime() - startNs));
        }
    }
}