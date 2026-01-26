package com.brutecx.docflow_backend.controller;

import com.brutecx.docflow_backend.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser() {


        // Implementation to retrieve the current user




        return ResponseEntity.noContent().build();
    }
}
