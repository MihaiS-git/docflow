package com.brutecx.docflow_backend.application.mail;

public interface IMailService {
    void sendMail(
            String to,
            String subject,
            String body
    );

    void sendInvite(
            String to,
            String inviteLink
    );
}
