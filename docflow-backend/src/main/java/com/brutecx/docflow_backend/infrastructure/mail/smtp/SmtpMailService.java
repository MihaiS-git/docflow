package com.brutecx.docflow_backend.infrastructure.mail.smtp;

import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.logging.InfraEventActions;
import com.brutecx.docflow_backend.logging.InfraEventLogger;
import com.brutecx.docflow_backend.logging.InfraEventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class SmtpMailService implements IMailService {

    private static final String STREAM = "SMTP";

    private final JavaMailSender mailSender;
    private final MeterRegistry meterRegistry;

    @Value("${MAIL_FROM}")
    private String from;

    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> rateLimitedCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    private static final int MAX_EMAILS_PER_WINDOW = 5;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(5);

    private final Map<String, RecipientWindow> recipientWindows = new ConcurrentHashMap<>();

    @Override
    public void sendMail(String to, String subject, String body) {
        if (isRateLimited(to)) {
            incrementRateLimited("MAIL");

            InfraEventLogger.blocked(
                    InfraEventType.MAIL,
                    InfraEventActions.MAIL_SMTP_SEND,
                    "rate_limited"
            );

            throw new IllegalStateException("SMTP rate limit exceeded");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        final long startNs = System.nanoTime();

        try {
            mailSender.send(message);
            incrementSuccess("MAIL");
        } catch (MailException ex) {
            incrementFailure("MAIL");

            InfraEventLogger.failure(
                    InfraEventType.MAIL,
                    "smtp_send",
                    "smtp_failure",
                    ex
            );

            throw ex;
        } finally {
            recordLatency("MAIL", Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    @Override
    public void sendInvite(
            Tenant tenant,
            String to,
            String firstName,
            String lastName,
            String jobTitle,
            String department,
            String inviteLink
    ) {
        String body = """
                
                Hello %s %s,
                
                You have been invited to join %s's %s department.

                Login details (temporary):
                Email: %s

                Accept the invitation using the link below:
                %s

                You will be required to change this password immediately after login.
                The invitation is valid 7 days.
                """.formatted(
                firstName,
                lastName,
                tenant.getName(),
                department,
                to,
                inviteLink
        );

        sendMail(to, "You're invited", body);
    }

    private boolean isRateLimited(String recipient) {
        RecipientWindow window = recipientWindows.computeIfAbsent(
                recipient,
                k -> new RecipientWindow()
        );

        synchronized (window) {
            Instant now = Instant.now();

            if (window.windowStart == null ||
                    now.isAfter(window.windowStart.plus(RATE_WINDOW))) {
                window.windowStart = now;
                window.count = 0;
            }

            if (window.count >= MAX_EMAILS_PER_WINDOW) {
                return true;
            }

            window.count++;
            return false;
        }
    }

    private static final class RecipientWindow {
        Instant windowStart;
        int count;
    }

    private void incrementSuccess(String context) {
        successCounters.computeIfAbsent(context, k ->
                Counter.builder("docflow_mail_send_success_total")
                        .tag("stream", STREAM)
                        .tag("context", context)
                        .register(meterRegistry)
        ).increment();
    }

    private void incrementFailure(String context) {
        failureCounters.computeIfAbsent(context, k ->
                Counter.builder("docflow_mail_send_failures_total")
                        .tag("stream", STREAM)
                        .tag("context", context)
                        .register(meterRegistry)
        ).increment();
    }

    private void incrementRateLimited(String context) {
        rateLimitedCounters.computeIfAbsent(context, k ->
                Counter.builder("docflow_mail_send_rate_limited_total")
                        .tag("stream", STREAM)
                        .tag("context", context)
                        .register(meterRegistry)
        ).increment();
    }

    private void recordLatency(String context, Duration duration) {
        if (duration == null || duration.isNegative()) return;

        latencyTimers.computeIfAbsent(context, k ->
                Timer.builder("docflow_mail_send_seconds")
                        .tag("stream", STREAM)
                        .tag("context", context)
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        ).record(duration);
    }

    @PostConstruct
    void init() {
        InfraEventLogger.success(
                InfraEventType.STARTUP,
                InfraEventActions.STARTUP_SERVICE_START
        );
    }
}