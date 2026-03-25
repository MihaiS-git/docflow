package com.brutecx.docflow_backend.testsupport.mail;

import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
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
            String inviteLink
    ) {

    }

    @PostConstruct
    void init() {
        log.warn("NOOP MAIL SERVICE ACTIVE");
    }
}
