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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    private final ObjectMapper objectMapper;
    private final KeycloakAdminPullProperties props;
    private final RestClient keycloakAdminRestClient;

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<>() {
    };
    private static final String ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";
    private static final String ACTION_VERIFY_EMAIL = "VERIFY_EMAIL";

    public List<KeycloakAdminEvent> fetchEvents(long sinceTimeMs) {
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
            throw new RestClientException("Keycloak events fetch failed", ex);
        }

        if (body == null || body.isBlank()) {
            throw new RestClientException("Keycloak events fetch failed: empty body");
        }

        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RestClientException("Failed to parse Keycloak events", e);
        }
    }

    public KeycloakUser fetchUser(String userId) {
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
                return null;
            }

            return objectMapper.readValue(body, KeycloakUser.class);
        } catch (RestClientException ex) {
            log.warn("Keycloak fetchUser failed for userId={}", userId, ex);
            return null; // user deleted or access revoked
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Keycloak user", ex);
        }
    }

    /* =========================
       TOKEN
       ========================= */
    private String fetchAccessToken() {
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
            throw new RestClientException("Keycloak token fetch failed", ex);
        }

        if (tokenResponse == null || tokenResponse.accessToken == null || tokenResponse.accessToken.isBlank()) {
            throw new RestClientException("Keycloak token fetch failed: empty token");
        }

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
        log.info("Keycloak - Assigning realm role '{}' to user '{}'", roleName, userId);
        String token = fetchAccessToken();
        log.info("Keycloak - Fetched access token for role assignment {}", token);
        Map<String, Object> role = fetchRealmRole(roleName, token);
        log.info("Keycloak - Fetched realm role details for '{}': {}", roleName, role);

        String url = props.baseUrl()
                + "/admin/realms/" + props.realm()
                + "/users/" + userId
                + "/role-mappings/realm";

        log.info("Keycloak - Assigning role via URL: {}", url);

        try {
            keycloakAdminRestClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Keycloak - Successfully assigned role via POST + keycloakAdminRestClient");
        } catch (Exception ex) {
            log.error("Keycloak assignRealmRole failed", ex);
            throw new RestClientException(
                    "Failed to assign realm role '" + roleName + "' to user " + userId,
                    ex
            );
        }
    }

    public void revokeRealmRole(String userId, String roleName) {
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
        } catch (Exception ex) {
            throw new RestClientException(
                    "Failed to revoke realm role '" + roleName + "' from user " + userId,
                    ex
            );
        }
    }

    private Map<String, Object> fetchRealmRole(String roleName, String token) {
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
                throw new RestClientException(
                        "Failed to fetch realm role '" + roleName + "': empty response"
                );
            }

            return objectMapper.readValue(
                    body,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (Exception ex) {
            throw new RestClientException(
                    "Failed to fetch realm role '" + roleName + "'",
                    ex
            );
        }
    }

    public List<String> fetchUserRealmRoles(String userId) {
        Objects.requireNonNull(userId, "userId");

        String token = fetchAccessToken();

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
                return List.of();
            }

            List<Map<String, Object>> roles = objectMapper.readValue(
                    body,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );

            return roles.stream()
                    .map(r -> (String) r.get("name"))
                    .filter(Objects::nonNull)
                    .toList();

        } catch (Exception ex) {
            throw new RestClientException(
                    "Failed to fetch realm roles for user " + userId,
                    ex
            );
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
        String userId = ensureInviteUserExists(email);
        setTemporaryPassword(userId, temporaryPassword);

        updateRequiredActions(userId, List.of(
                ACTION_UPDATE_PASSWORD,
                ACTION_VERIFY_EMAIL
        ));

        return userId;
    }

    // Admin API reset-password call
    private void setTemporaryPassword(String userId, String password) {
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
        } catch (Exception ex) {
            throw new RestClientException(
                    "Failed to set temporary password for user " + userId,
                    ex
            );
        }
    }

    private String ensureInviteUserExists(String email) {
        String userId = findUserIdByEmailOrUsername(email);
        if (userId != null) {
            return userId;
        }
        return createUserInviteOnly(email);
    }

    private String findUserIdByEmailOrUsername(String email) {
        String token = fetchAccessToken();
        String url = adminBaseUrl() + "/users?search=" + urlEncode(email);

        String body = keycloakAdminRestClient.get()
                .uri(url)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank()) return null;

        List<Map<String, Object>> users;
        try {
            users = objectMapper.readValue(body, LIST_OF_MAP);
        } catch (Exception ex) {
            throw new RestClientException("Keycloak user search parse failed", ex);
        }

        for (Map<String, Object> u : users) {
            if (email.equalsIgnoreCase(String.valueOf(u.get("email")))
                    || email.equalsIgnoreCase(String.valueOf(u.get("username")))) {
                return String.valueOf(u.get("id"));
            }
        }
        return null;
    }

    private String createUserInviteOnly(String email) {
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
            return idFromLocation;
        }

        String fallback = findUserIdByEmailOrUsername(email);
        if (fallback == null) {
            throw new IllegalStateException("Keycloak user created but id not resolvable");
        }
        return fallback;
    }

    private void updateRequiredActions(String userId, List<String> actions) {
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
        } catch (Exception ex) {
            throw new RestClientException("Failed to update requiredActions", ex);
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
        Objects.requireNonNull(subjectId, "subjectId");

        String token = fetchAccessToken();
        String url = adminBaseUrl() + "/users/" + subjectId;

        try {
            keycloakAdminRestClient.delete()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new RestClientException(
                    "Failed to delete Keycloak user " + subjectId,
                    ex
            );
        }
    }
}
