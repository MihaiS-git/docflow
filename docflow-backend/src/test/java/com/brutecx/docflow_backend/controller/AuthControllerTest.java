package com.brutecx.docflow_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import com.brutecx.docflow_backend.security.AuthRoleExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class AuthControllerTest {

    private final AuthController controller =
            new AuthController(new AuthRoleExtractor());

    @Test
    void me_returns401_whenAuthenticationIsNull() {
        ResponseEntity<?> res = controller.me(null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void me_returns401_whenAnonymousAuthentication() {
        AnonymousAuthenticationToken anon = mock(AnonymousAuthenticationToken.class);
        when(anon.isAuthenticated()).thenReturn(true);

        ResponseEntity<?> res = controller.me(anon);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void me_returnsUserAndFilteredRoles_whenOidcUser() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getPreferredUsername()).thenReturn("mihai");
        when(oidcUser.getEmail()).thenReturn("mihai@example.com");
        when(auth.getPrincipal()).thenReturn(oidcUser);

        doReturn(List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_default-roles-docflow"),
                new SimpleGrantedAuthority("ROLE_uma_authorization"),
                new SimpleGrantedAuthority("SCOPE_openid")
        )).when(auth).getAuthorities();

        ResponseEntity<?> res = controller.me(auth);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertThat(body.get("username")).isEqualTo("mihai");
        assertThat(body.get("email")).isEqualTo("mihai@example.com");
        assertThat((List<String>) body.get("roles")).containsExactly("ADMIN", "USER");
    }

    @Test
    @SuppressWarnings("unchecked")
    void me_returnsUsernameFromAuthentication_whenNonOidcPrincipal() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("non-oidc-principal");
        when(auth.getName()).thenReturn("service-user");

        doReturn(List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("SCOPE_openid")
        )).when(auth).getAuthorities();

        ResponseEntity<?> res = controller.me(auth);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertThat(body.get("username")).isEqualTo("service-user");
        assertThat((List<String>) body.get("roles")).containsExactly("USER");
    }


}
