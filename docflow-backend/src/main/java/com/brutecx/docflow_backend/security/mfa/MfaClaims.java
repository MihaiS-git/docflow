package com.brutecx.docflow_backend.security.mfa;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.List;
import java.util.Map;

final class MfaClaims {

    private MfaClaims() {}

    static boolean hasMfa(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof OidcUser user)) {
            return false;
        }

        Map<String, Object> claims = user.getClaims();

        // amr: array claim
        Object amr = claims.get("amr");
        if (amr instanceof List<?> list) {
            return list.contains("otp") || list.contains("mfa");
        }

        // acr: string claim
        Object acr = claims.get("acr");
        if (acr instanceof String s) {
            return s.equalsIgnoreCase("mfa")
                    || s.equalsIgnoreCase("urn:mace:incommon:iap:silver");
        }

        return false;
    }
}
