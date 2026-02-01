package com.brutecx.docflow_backend.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record representing a Keycloak user with relevant properties.
 * Includes a method to get the display name of the user.
 */
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