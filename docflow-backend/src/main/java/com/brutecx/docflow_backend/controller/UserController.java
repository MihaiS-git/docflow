package com.brutecx.docflow_backend.controller;

import com.brutecx.docflow_backend.user.UserResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUserData() {

        return ResponseEntity.noContent().build();
    }
}
