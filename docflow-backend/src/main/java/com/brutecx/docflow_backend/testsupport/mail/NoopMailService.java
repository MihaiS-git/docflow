package com.brutecx.docflow_backend.testsupport.mail;

import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class NoopMailService implements IMailService {


    @Override
    public void sendMail(String to, String subject, String body) {

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

    }
}
