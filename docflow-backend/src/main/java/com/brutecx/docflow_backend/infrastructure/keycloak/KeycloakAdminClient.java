package com.brutecx.docflow_backend.infrastructure.keycloak;

import com.brutecx.docflow_backend.api.error.InviteDeliveryException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

//    @Value("${KC_HOSTNAME}")
//    private String kcBaseUrl;

    private final ObjectMapper objectMapper;
    private final KeycloakAdminPullProperties props;
    private final RestClient keycloakAdminRestClient;

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<>() {
    };
    private static final String ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";
    private static final String ACTION_CONFIGURE_TOTP = "CONFIGURE_TOTP";


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
     * Invite-only onboarding: ensure a user exists (username=email), without setting any password.
     * Then send a Keycloak "execute actions" email link so the user can set the password BEFORE any login attempt.
     */
    public String ensureInviteUserExists(String email) {
        String userId = findUserIdByEmailOrUsername(email);

        if (userId == null) {
            userId = createUserInviteOnly(email);
        }

        return userId;
    }

    public void sendInvitePasswordSetupEmail(
            String userId,
            String clientId,
            String redirectUri,
            int lifespanSeconds
    ) {
        String token = fetchAccessToken();

        log.info("=== KEYCLOAK INVITE EMAIL DEBUG ===");
        log.info("Client ID: {}", clientId);
        log.info("Redirect URI (raw): {}", redirectUri);
        log.info("Redirect URI (encoded): {}", urlEncode(redirectUri));
        log.info("===================================");

        String url = adminBaseUrl()
                + "/users/" + userId
                + "/execute-actions-email"
                + "?lifespan=" + lifespanSeconds
                + "&client_id=" + urlEncode(clientId)
                + "&redirect_uri=" + urlEncode(redirectUri);

        log.info("Full Keycloak URL: {}", url);

        try {
            ResponseEntity<String> resp = keycloakAdminRestClient.put()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of())
                    .retrieve()
                    .toEntity(String.class);

            log.info("Keycloak execute-actions-email status={} body={}",
                    resp.getStatusCode(), resp.getBody());

        } catch (HttpClientErrorException e) {
            log.error("Keycloak error body: {}", e.getResponseBodyAsString());
            throw new InviteDeliveryException(
                    e.getStatusCode(),
                    "Keycloak execute-actions-email failed: " + e.getResponseBodyAsString()
            );
        }
    }


    private String findUserIdByEmailOrUsername(String email) {
        String token = fetchAccessToken();
        String url = adminBaseUrl() + "/users?max=20&search=" + urlEncode(email);

        String body;
        try {
            body = keycloakAdminRestClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new RestClientException("Keycloak user search failed", ex);
        }

        if (body == null || body.isBlank()) return null;

        final List<Map<String, Object>> users;
        try {
            users = objectMapper.readValue(body, LIST_OF_MAP);
        } catch (Exception ex) {
            throw new RestClientException("Keycloak user search parse failed", ex);
        }

        if (users.isEmpty()) return null;

        for (Map<String, Object> u : users) {
            Object username = u.get("username");
            Object mail = u.get("email");
            if (email.equalsIgnoreCase(String.valueOf(username)) || email.equalsIgnoreCase(String.valueOf(mail))) {
                Object id = u.get("id");
                return id == null ? null : String.valueOf(id);
            }
        }

        return null;
    }

    private String createUserInviteOnly(String email) {
        String token = fetchAccessToken();
        String url = adminBaseUrl() + "/users";

        Map<String, Object> payload = Map.of(
                "username", email,
                "email", email,
                "enabled", true,
                "emailVerified", false
        );

        ResponseEntity<Void> resp;
        try {
            resp = keycloakAdminRestClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new RestClientException("Keycloak create user failed", ex);
        }

        // Prefer Location header (fast, reliable); fallback to re-query.
        String location = resp.getHeaders().getFirst(HttpHeaders.LOCATION);
        String idFromLocation = extractUserIdFromLocation(location);
        if (idFromLocation != null && !idFromLocation.isBlank()) {
            return idFromLocation;
        }

        String id = findUserIdByEmailOrUsername(email);
        if (id == null)
            throw new IllegalStateException("Keycloak user created but id cannot be resolved for: " + email);
        return id;
    }

    // local helpers (no new concepts/files)
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

    // CHANGED: merge requiredActions instead of overwrite
    public void setRequiredActions(String userId, List<String> actions) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(actions, "actions");

        // fetch existing required actions
        List<String> existing = fetchRequiredActions(userId);

        // merge (preserve + add)
        List<String> merged = existing.stream().toList();
        for (String a : actions) {
            if (!merged.contains(a)) {
                merged = new java.util.ArrayList<>(merged);
                merged.add(a);
            }
        }

        String token = fetchAccessToken();
        String url = adminBaseUrl() + "/users/" + userId;

        try {
            keycloakAdminRestClient.put()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("requiredActions", merged))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new RestClientException(
                    "Keycloak merge requiredActions failed for user " + userId,
                    ex
            );
        }
    }

    public String ensureInviteUserExistsWithRequiredActions(String email) {
        String userId = ensureInviteUserExists(email);

        List<String> existing = fetchRequiredActions(userId);

        List<String> merged = new java.util.ArrayList<>(existing);

        if (!merged.contains("VERIFY_EMAIL")) {
            merged.add("VERIFY_EMAIL");
        }
        if (!merged.contains("UPDATE_PASSWORD")) {
            merged.add("UPDATE_PASSWORD");
        }

        setRequiredActions(userId, merged);
        return userId;
    }




    public List<String> fetchRequiredActions(String userId) {
        String token = fetchAccessToken();
        String url = adminBaseUrl() + "/users/" + userId;

        try {
            String body = keycloakAdminRestClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return List.of();
            }

            Map<String, Object> user =
                    objectMapper.readValue(body, new TypeReference<>() {});

            Object raw = user.get("requiredActions");
            if (raw instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }

            return List.of();
        } catch (Exception ex) {
            throw new RestClientException("Failed to fetch requiredActions", ex);
        }
    }


}
