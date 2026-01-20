package com.brutecx.docflow_backend.security.audit.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.TokenResponse;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class KeycloakAdminClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final KeycloakAdminPullProperties props;

    public KeycloakAdminClient(
            ObjectMapper objectMapper,
            KeycloakAdminPullProperties props
    ) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.props = props;
    }

    public List<KeycloakAdminEvent> fetchEvents(long sinceTimeMs) {
        String token = fetchAccessToken();

        String url = props.baseUrl()
                + "/admin/realms/" + props.realm()
                + "/events"
                + "?dateFrom=" + (sinceTimeMs > 0 ? sinceTimeMs : 0)
                + "&max=" + props.pageSize();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> res = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new RestClientException("Keycloak events fetch failed: " + res.getStatusCode());
        }

        try {
            return objectMapper.readValue(res.getBody(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RestClientException("Failed to parse Keycloak events", e);
        }
    }

    private String fetchAccessToken() {
        String tokenUrl = props.baseUrl()
                + "/realms/" + props.realm()
                + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());

        ResponseEntity<TokenResponse> res = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                TokenResponse.class
        );

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null || res.getBody().accessToken == null) {
            throw new RestClientException("Keycloak token fetch failed: " + res.getStatusCode());
        }
        return res.getBody().accessToken;
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

}
