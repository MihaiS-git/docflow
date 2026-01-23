package com.brutecx.docflow_backend.security.mfa;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads assurance-related claims from the authenticated OIDC principal.
 * <p>
 * Keycloak may provide:
 * - amr: list of authentication methods (e.g. ["pwd","otp"])
 * - acr: authentication context class reference (realm-defined)
 */
public final class OidcAssuranceClaims {

    private OidcAssuranceClaims() {
    }

    public static Optional<List<String>> amr(Authentication authentication) {
        OidcUser user = oidcUser(authentication);
        if (user == null) {
            return Optional.empty();
        }

        Object raw = user.getIdToken().getClaims().get("amr");
        if (raw instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> rawList = (List<Object>) list;
            List<String> values = rawList.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
            return Optional.of(values);
        }

        return Optional.empty();
    }


    public static Optional<String> acr(Authentication authentication) {
        OidcUser user = oidcUser(authentication);
        if (user == null) {
            return Optional.empty();
        }
        Object raw = user.getIdToken().getClaims().get("acr");
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(raw.toString());
    }

    /**
     * Conservative MFA detection:
     * - amr contains otp/webauthn/mfa
     * - OR acr contains "mfa" (realm-specific convention)
     * <p>
     * If claims are missing -> treated as NOT MFA.
     */
    public static boolean hasMfa(Authentication authentication) {
        Optional<List<String>> amrOpt = amr(authentication);
        if (amrOpt.isPresent()) {
            Collection<String> amr = amrOpt.get();
            return amr.stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .anyMatch(v -> v.contains("otp") || v.contains("webauthn") || v.contains("mfa"));
        }

        Optional<String> acrOpt = acr(authentication);
        return acrOpt
                .map(v -> v.toLowerCase(Locale.ROOT).contains("mfa"))
                .orElse(false);
    }

    private static OidcUser oidcUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser;
        }
        return null;
    }
}
