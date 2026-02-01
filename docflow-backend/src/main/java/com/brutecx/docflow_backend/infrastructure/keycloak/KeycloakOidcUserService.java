package com.brutecx.docflow_backend.infrastructure.keycloak;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Custom OidcUserService to map Keycloak roles to Spring Security authorities.
 * Extracts roles from Keycloak-specific claims and converts them to ROLE_* format.
 * This service can be configured in the Spring Security setup to replace the default OidcUserService.
 */
@Component
public class KeycloakOidcUserService extends OidcUserService {

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        Set<GrantedAuthority> mappedAuthorities = new LinkedHashSet<>();

        // keep already present authorities (rarely contains roles)
        mappedAuthorities.addAll(oidcUser.getAuthorities());

        // add mapped ROLE_* authorities from Keycloak claims
        mappedAuthorities.addAll(extractKeycloakRoles(oidcUser));

        return new DefaultOidcUser(
                mappedAuthorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "preferred_username"
        );
    }

    private Collection<? extends GrantedAuthority> extractKeycloakRoles(OidcUser oidcUser) {
        Set<String> roles = new LinkedHashSet<>();

        Map<String, Object> claims = oidcUser.getClaims();

        // realm_access.roles
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object realmRoles = map.get("roles");
            if (realmRoles instanceof Collection<?> list) {
                list.forEach(r -> roles.add(String.valueOf(r)));
            }
        }

        // resource_access.<client>.roles
        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> map) {
            for (Object clientObj : map.values()) {
                if (!(clientObj instanceof Map<?, ?> clientMap)) continue;
                Object clientRoles = clientMap.get("roles");
                if (clientRoles instanceof Collection<?> list) {
                    list.forEach(r -> roles.add(String.valueOf(r)));
                }
            }
        }

        // convert to Spring format ROLE_X
        return roles.stream()
                .filter(r -> r != null && !r.isBlank())
                // remove noise
                .filter(r -> !r.startsWith("default-roles-"))
                .filter(r -> !r.equalsIgnoreCase("uma_authorization"))
                .filter(r -> !r.equalsIgnoreCase("offline_access"))
                .map(r -> "ROLE_" + r.toUpperCase(Locale.ROOT))
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
