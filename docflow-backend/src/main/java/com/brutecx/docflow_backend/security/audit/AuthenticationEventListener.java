package com.brutecx.docflow_backend.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuthenticationEventListener {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final AuthenticationEventRepository repository;
    private final HttpServletRequest request;

    public AuthenticationEventListener(
            AuthenticationEventRepository repository,
            HttpServletRequest request
    ) {
        this.repository = repository;
        this.request = request;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        persist(AuthenticationResult.SUCCESS, event.getAuthentication().getName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        persist(AuthenticationResult.FAILURE, event.getAuthentication().getName());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        persist(AuthenticationResult.LOGOUT, event.getAuthentication().getName());
    }

    private void persist(AuthenticationResult result, String username) {
        String correlationId = request.getHeader("X-Correlation-Id") != null
                ? request.getHeader("X-Correlation-Id")
                : UUID.randomUUID().toString();

        AuthenticationEvent entity = new AuthenticationEvent(
                Instant.now(),
                username,
                result,
                "KEYCLOAK",
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                correlationId
        );

        repository.save(entity);

        log.info(
                "auth_event result={} username={} idp={} ip={} ua={} correlationId={}",
                result,
                username,
                "KEYCLOAK",
                entity.getIp(),
                entity.getUserAgent(),
                correlationId
        );
    }

}
