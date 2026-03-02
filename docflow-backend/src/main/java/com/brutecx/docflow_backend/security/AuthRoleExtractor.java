package com.brutecx.docflow_backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Utility component to extract and normalize application roles
 * from a Spring Security Authentication.
 * - Keeps only ROLE_ authorities
 * - Strips ROLE_ prefix
 * - Removes Keycloak noise roles
 * - Normalizes to uppercase
 * - Returns deterministic ordering
 * This class is intentionally side-effect free:
 * - No logging
 * - No metrics
 * - No audit emission
 */
@Component
public class AuthRoleExtractor {

    public List<String> extractRoles(Authentication authentication) {

        if (authentication == null) {
            return List.of();
        }

        Collection<? extends GrantedAuthority> authorities =
                authentication.getAuthorities();

        if (authorities == null || authorities.isEmpty()) {
            return List.of();
        }

        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .filter(role -> !role.startsWith("default-roles-"))
                .filter(role -> !role.equalsIgnoreCase("uma_authorization"))
                .filter(role -> !role.equalsIgnoreCase("offline_access"))
                .map(role -> role.toUpperCase(Locale.ROOT))
                .sorted()
                .toList();
    }

    public List<String> filterRealmRoles(Collection<String> roles) {

        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .filter(role -> !role.startsWith("DEFAULT-ROLES-"))
                .filter(role -> !role.equals("OFFLINE_ACCESS"))
                .filter(role -> !role.equals("UMA_AUTHORIZATION"))
                .sorted()
                .toList();
    }
}