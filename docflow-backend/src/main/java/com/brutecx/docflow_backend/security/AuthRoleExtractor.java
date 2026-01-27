package com.brutecx.docflow_backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Utility component to extract and filter roles from an Authentication object.
 * Converts roles to uppercase and removes Keycloak-specific noise roles.
 * Used by controllers and other components to get a clean list of user roles.
 */
@Component
public class AuthRoleExtractor {

    public List<String> extractRoles(Authentication authentication) {
        if (authentication == null) {
            return Collections.emptyList();
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                // only keep ROLE_ authorities
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                // remove Keycloak noise roles
                .filter(role -> !role.startsWith("default-roles-"))
                .filter(role -> !role.equalsIgnoreCase("uma_authorization"))
                .filter(role -> !role.equalsIgnoreCase("offline_access"))
                .map(role -> role.toUpperCase(Locale.ROOT))
                .sorted()
                .toList();
    }

    public List<String> filterRealmRoles(Collection<String> roles) {
        if (roles == null) {
            return List.of();
        }

        return roles.stream()
                .map(String::toUpperCase)
                .filter(role -> !role.startsWith("DEFAULT-ROLES-"))
                .filter(role -> !role.equals("OFFLINE_ACCESS"))
                .filter(role -> !role.equals("UMA_AUTHORIZATION"))
                .sorted()
                .toList();
    }
}
