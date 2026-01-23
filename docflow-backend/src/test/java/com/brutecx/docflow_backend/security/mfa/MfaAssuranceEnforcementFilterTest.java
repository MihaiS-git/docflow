package com.brutecx.docflow_backend.security.mfa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MfaAssuranceEnforcementFilterTest.TestEndpoints.class)
class MfaAssuranceEnforcementFilterTest {

    @Autowired
    MockMvc mockMvc;

    @RestController
    @RequestMapping
    static class TestEndpoints {

        @GetMapping("/api/review/ping")
        String reviewPing() {
            return "ok";
        }

        @GetMapping("/api/admin/ping")
        String adminPing() {
            return "ok";
        }
    }

    @Test
    void reviewEndpoint_forbidden_whenMissingMfa() throws Exception {
        OAuth2AuthenticationToken auth = oidcAuthWithClaims(Map.of(), "REVIEWER");

        mockMvc.perform(get("/api/review/ping")
                        .with(authentication(auth)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("mfa_required"));
    }

    @Test
    void reviewEndpoint_allows_whenAmrContainsOtp() throws Exception {
        OAuth2AuthenticationToken auth = oidcAuthWithClaims(
                Map.of("amr", List.of("pwd", "otp")),
                "REVIEWER"
        );

        mockMvc.perform(get("/api/review/ping")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void adminEndpoint_allows_whenAcrContainsMfa() throws Exception {
        OAuth2AuthenticationToken auth = oidcAuthWithClaims(
                Map.of("acr", "mfa"),
                "ADMIN"
        );

        mockMvc.perform(get("/api/admin/ping")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    private static OAuth2AuthenticationToken oidcAuthWithClaims(
            Map<String, Object> extraClaims,
            String... roles
    ) {
        Instant now = Instant.now();

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "test-subject-123");
        claims.putAll(extraClaims);

        OidcIdToken idToken = new OidcIdToken(
                "fake-token",
                now.minusSeconds(10),
                now.plusSeconds(600),
                claims
        );

        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        OidcUser user = new DefaultOidcUser(authorities, idToken);

        return new OAuth2AuthenticationToken(
                user,
                user.getAuthorities(),
                "keycloak"
        );
    }
}
