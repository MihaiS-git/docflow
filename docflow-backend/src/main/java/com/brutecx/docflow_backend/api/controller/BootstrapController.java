package com.brutecx.docflow_backend.api.controller;

import com.brutecx.docflow_backend.domain.user.BootstrapActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final BootstrapActivationService bootstrapActivationService;

    @PostMapping("/activate")
    public void activate(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new IllegalStateException("OIDC authentication required");
        }

        bootstrapActivationService.activateBootstrapAdmin(
                oidcUser.getSubject()
        );
    }
}
