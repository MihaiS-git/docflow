package com.brutecx.docflow_backend.api.controller.admin.user;

import com.brutecx.docflow_backend.api.dto.admin.AdminUserResponseDTO;
import com.brutecx.docflow_backend.domain.admin.AdminUserService;
import com.brutecx.docflow_backend.domain.admin.UserRoleAdminService;
import com.brutecx.docflow_backend.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserRoleAdminService userRoleAdminService;

    @GetMapping
    public ResponseEntity<Page<AdminUserResponseDTO>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String email
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, sort)
        );

        return ResponseEntity.ok(
                adminUserService.listUsers(
                        pageable,
                        tenantId,
                        status,
                        email
                )
        );
    }

    @GetMapping("/roles")
    public ResponseEntity<List<String>> listAssignableRoles() {
        return ResponseEntity.ok(List.of(
                "ADMIN",
                "AUDITOR"
        ));
    }

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
