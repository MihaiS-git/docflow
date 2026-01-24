package com.brutecx.docflow_backend.controller;

import java.util.Map;
import java.util.stream.Collectors;

import com.brutecx.docflow_backend.security.AuthRoleExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to handle authentication-related endpoints.
 * Provides an endpoint to retrieve information about the currently authenticated user.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthRoleExtractor authRoleExtractor;

    public AuthController(AuthRoleExtractor authRoleExtractor) {
        this.authRoleExtractor = authRoleExtractor;
    }


    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var roles = authRoleExtractor.extractRoles(authentication);

        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return ResponseEntity.ok(Map.of(
                    "username", oidcUser.getPreferredUsername(),
                    "email", oidcUser.getEmail(),
                    "roles", roles
            ));
        }

        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "roles", roles
        ));
    }

}
