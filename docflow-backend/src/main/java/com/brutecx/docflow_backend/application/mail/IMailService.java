package com.brutecx.docflow_backend.application.mail;

import com.brutecx.docflow_backend.domain.tenant.Tenant;

public interface IMailService {
    void sendMail(
            String to,
            String subject,
            String body
    );

    void sendInvite(
            Tenant tenant,
            String to,
            String firstName,
            String lastName,
            String jobTitle,
            String department,
            String inviteLink,
            String temporaryPassword
    );
}
