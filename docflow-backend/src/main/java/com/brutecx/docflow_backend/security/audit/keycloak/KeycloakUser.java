package com.brutecx.docflow_backend.security.audit.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(

        @JsonProperty("id")
        String id,

        @JsonProperty("username")
        String username,

        @JsonProperty("email")
        String email,

        @JsonProperty("firstName")
        String firstName,

        @JsonProperty("lastName")
        String lastName
) {

    public String displayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (firstName != null) return firstName;
        if (lastName != null) return lastName;
        return username;
    }
}