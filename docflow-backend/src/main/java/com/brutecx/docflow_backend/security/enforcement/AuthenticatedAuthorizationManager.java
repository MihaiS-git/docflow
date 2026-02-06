package com.brutecx.docflow_backend.security.enforcement;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class AuthenticatedAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authSupplier,
            RequestAuthorizationContext context
    ) {
        Authentication auth = authSupplier.get();
        return new AuthorizationDecision(
                auth != null
                        && auth.isAuthenticated()
                        && !(auth instanceof AnonymousAuthenticationToken)
        );
    }
}