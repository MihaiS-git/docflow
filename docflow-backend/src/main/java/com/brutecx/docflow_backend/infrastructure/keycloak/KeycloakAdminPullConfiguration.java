package com.brutecx.docflow_backend.infrastructure.keycloak;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuration class for Keycloak admin user pull.
 * Sets up the RestClient with custom timeouts.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(KeycloakAdminPullProperties.class)
public class KeycloakAdminPullConfiguration {

    @Bean
    RestClient keycloakAdminRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return builder
                .requestFactory(requestFactory)
                .build();
    }

}
