package com.brutecx.docflow_backend.admin;

import com.brutecx.docflow_backend.user.AdminUserResponseDTO;
import com.brutecx.docflow_backend.user.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/{userId}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable UUID userId) {
        adminUserService.activateUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/lock")
    public ResponseEntity<Void> lockUser(@PathVariable UUID userId) {
        adminUserService.lockUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/disable")
    public ResponseEntity<Void> disableUser(@PathVariable UUID userId) {
        adminUserService.disableUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponseDTO>> listUsers() {
        List<AdminUserResponseDTO> users = adminUserService.listUsers();

        return ResponseEntity.ok(users);
    }
}
