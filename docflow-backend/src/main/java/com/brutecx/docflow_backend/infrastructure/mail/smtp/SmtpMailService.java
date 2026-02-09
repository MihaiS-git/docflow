package com.brutecx.docflow_backend.infrastructure.mail.smtp;

import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class SmtpMailService implements IMailService {

    private final JavaMailSender mailSender;

    @Value("${MAIL_FROM}")
    private String from;

    @Override
    public void sendMail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }

    @Override
    public void sendInvite(
            Tenant tenant,
            String to,
            String firstName,
            String lastName,
            String jobTitle,
            String department,
            String inviteLink,
            String temporaryPassword
    ) {
        String body = """
                
                Hello %s %s,
                
                You have been invited to join %s's %s department.

                Login details (temporary):
                Email: %s
                Temporary password: %s

                Accept the invitation using the link below:
                %s

                You will be required to change this password immediately after login.
                The invitation is valid 7 days.
                """.formatted(firstName, lastName, tenant.getName(), department, to, temporaryPassword, inviteLink);

        sendMail(to, "You're invited", body);
    }

    @PostConstruct
    void init() {
        log.warn("SMTP MAIL SERVICE ACTIVE");
    }

}
