package com.brutecx.docflow_backend.api.controller;

import com.brutecx.docflow_backend.api.dto.user.LocalUserDTO;
import com.brutecx.docflow_backend.api.error.ErrorCode;
import com.brutecx.docflow_backend.api.error.LifecycleAccessDeniedException;
import com.brutecx.docflow_backend.domain.user.CurrentUserResult;
import com.brutecx.docflow_backend.domain.user.UserService;
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
    public ResponseEntity<LocalUserDTO> getCurrentUserData() {
        CurrentUserResult result = userService.resolveCurrentUser();

        return switch (result.state()) {
            case BOOTSTRAP -> ResponseEntity.noContent().build();

            case LOCKED -> throw new LifecycleAccessDeniedException(
                    ErrorCode.ACCOUNT_LOCKED,
                    "User account is locked"
            );

            case DISABLED -> throw new LifecycleAccessDeniedException(
                    ErrorCode.FORBIDDEN,
                    "User account is disabled"
            );

            case ACTIVE -> ResponseEntity.ok(
                    LocalUserDTO.from(result.user())
            );
        };
    }
}