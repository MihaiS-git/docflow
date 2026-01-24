package com.brutecx.docflow_backend.security.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to enable session security properties.
 */
@Configuration
@EnableConfigurationProperties(SessionSecurityProperties.class)
public class SessionSecurityConfig {

}
