package com.brutecx.docflow_backend.security.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SessionSecurityProperties.class)
public class SessionSecurityConfig {

}
