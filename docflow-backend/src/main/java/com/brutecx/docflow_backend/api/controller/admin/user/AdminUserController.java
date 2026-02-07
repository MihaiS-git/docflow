package com.brutecx.docflow_backend.api.controller.admin.user;

import com.brutecx.docflow_backend.api.dto.admin.AdminUserResponseDTO;
import com.brutecx.docflow_backend.domain.admin.AdminUserService;
import com.brutecx.docflow_backend.domain.admin.UserRoleAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserRoleAdminService userRoleAdminService;


    @GetMapping
    public ResponseEntity<List<AdminUserResponseDTO>> listUsers() {
        return ResponseEntity.ok(adminUserService.listUsers());
    }

    @GetMapping("/roles")
    public ResponseEntity<List<String>> listAssignableRoles() {
        return ResponseEntity.ok(List.of(
                "ADMIN",
                "AUDITOR",
                "REVIEWER"
        ));
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable UUID userId) {
        adminUserService.activateUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/lock")
    public ResponseEntity<Void> lockUser(@PathVariable UUID userId) {
        log.info("Controller - Locking user with id: {}", userId);
        adminUserService.lockUser(userId);
        log.info("Controller - User with id: {} locked", userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/disable")
    public ResponseEntity<Void> disableUser(@PathVariable UUID userId) {
        adminUserService.disableUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/assign-role")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID userId,
            @RequestParam String roleName
    ) {
        userRoleAdminService.assignRole(userId, roleName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/revoke-role")
    public ResponseEntity<Void> revokeRole(
            @PathVariable UUID userId,
            @RequestParam String roleName
    ) {
        userRoleAdminService.revokeRole(userId, roleName);
        return ResponseEntity.noContent().build();
    }

}
