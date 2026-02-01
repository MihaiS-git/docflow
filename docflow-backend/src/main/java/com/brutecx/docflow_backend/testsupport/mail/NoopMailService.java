package com.brutecx.docflow_backend.testsupport.mail;

import com.brutecx.docflow_backend.application.mail.IMailService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class NoopMailService implements IMailService {

    @Override
    public void sendMail(String to, String subject, String body) {
        // no-op
    }

    @Override
    public void sendInvite(String to, String inviteLink) {
        // no-op
    }
}
