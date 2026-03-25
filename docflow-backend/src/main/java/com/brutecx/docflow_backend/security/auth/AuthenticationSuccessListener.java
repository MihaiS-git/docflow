package com.brutecx.docflow_backend.security.auth;

import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import com.brutecx.docflow_backend.logging.InfraEventActions;
import com.brutecx.docflow_backend.logging.InfraEventLogger;
import com.brutecx.docflow_backend.logging.InfraEventOutcome;
import com.brutecx.docflow_backend.logging.InfraEventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationSuccessListener {

    private static final String STREAM = "AUTH_INFRA";

    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;

    private Counter successCounter;
    private Counter failureCounter;
    private Timer latencyTimer;

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        final long startNs = System.nanoTime();

        Authentication authentication = event.getAuthentication();

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return;
        }

        String subject = oidcUser.getSubject();

        try {
            Optional<User> existing = userRepository.findByExternalSubjectId(subject);

            existing.ifPresent(user -> {
                updateLastLogin(user);
                userRepository.save(user);
            });

            successCounter().increment();

            InfraEventLogger.log(
                    InfraEventType.AUTHENTICATION,
                    InfraEventActions.AUTHN_LAST_LOGIN_UPDATE,
                    InfraEventOutcome.SUCCESS,
                    null,
                    null,
                    StructuredArguments.kv("actor.subject_id", subject)
            );

        } catch (Exception ex) {
            failureCounter().increment();

            InfraEventLogger.log(
                    InfraEventType.AUTHENTICATION,
                    InfraEventActions.AUTHN_LAST_LOGIN_UPDATE,
                    InfraEventOutcome.FAILURE,
                    "database_write_failure",
                    ex,
                    StructuredArguments.kv("actor.subject_id", subject)
            );

            throw ex;

        } finally {
            latencyTimer().record(Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    private void updateLastLogin(User user) {
        user.setLastLoginAt(Instant.now());

        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            user.setLastLoginIp(req.getRemoteAddr());
            user.setLastLoginUserAgent(req.getHeader("User-Agent"));
        }
    }

    private Counter successCounter() {
        if (successCounter == null) {
            successCounter = Counter.builder("docflow_auth_last_login_update_success_total")
                    .tag("stream", STREAM)
                    .register(meterRegistry);
        }
        return successCounter;
    }

    private Counter failureCounter() {
        if (failureCounter == null) {
            failureCounter = Counter.builder("docflow_auth_last_login_update_failure_total")
                    .tag("stream", STREAM)
                    .register(meterRegistry);
        }
        return failureCounter;
    }

    private Timer latencyTimer() {
        if (latencyTimer == null) {
            latencyTimer = Timer.builder("docflow_auth_last_login_update_seconds")
                    .tag("stream", STREAM)
                    .publishPercentileHistogram()
                    .register(meterRegistry);
        }
        return latencyTimer;
    }
}