package com.brutecx.docflow_backend.api.dto.user;

import com.brutecx.docflow_backend.domain.user.User;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {

    public static UserResponseDTO toDto(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .displayName(user.getDisplayName())
                .status(user.getStatus())
                .build();
    }
}
