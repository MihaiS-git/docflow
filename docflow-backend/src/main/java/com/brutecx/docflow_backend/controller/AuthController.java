package com.brutecx.docflow_backend.controller;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Helper to extract and clean roles
        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                // 1. Only keep the roles mapped (which start with ROLE_)
                .filter(role -> role.startsWith("ROLE_"))
                // 2. Remove internal Keycloak noise
                .filter(role -> !role.contains("default-roles") && !role.equals("ROLE_uma_authorization") && !role.equals("ROLE_offline_access"))
                // 3. Optional: Remove "ROLE_" prefix so frontend gets "USER", "ADMIN"
                .map(role -> role.replace("ROLE_", ""))
                .sorted()
                .collect(Collectors.toList());

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
