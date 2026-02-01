package com.brutecx.docflow_backend.infrastructure.mail.smtp;

import com.brutecx.docflow_backend.application.mail.IMailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Profile({"dev", "prod"})
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
    public void sendInvite(String to, String inviteLink) {
        String body = """
                You have been invited.

                Accept the invitation using the link below:
                %s
                """.formatted(inviteLink);

        sendMail(to, "You're invited", body);
    }
}
