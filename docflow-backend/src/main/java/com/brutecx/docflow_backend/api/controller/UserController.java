package com.brutecx.docflow_backend.api.controller;

import com.brutecx.docflow_backend.api.dto.user.UserResponseDTO;
import com.brutecx.docflow_backend.api.dto.user.UserResponseMapper;
import com.brutecx.docflow_backend.domain.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUserData() {
        CurrentUserResult result = userService.resolveCurrentUser();

        return switch (result.state()) {
            case BOOTSTRAP ->
                    ResponseEntity.noContent().build();

            case LOCKED ->
                    ResponseEntity.status(403)
                            .body(null); // handled by global exception / error mapper

            case DISABLED ->
                    ResponseEntity.status(403)
                            .body(null);

            case ACTIVE ->
                    ResponseEntity.ok(
                            UserResponseMapper.toDto(result.user())
                    );
        };
    }
}
