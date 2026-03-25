package com.brutecx.docflow_backend.api.controller.admin.invite;

import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Profile({"dev", "prod", "test"})
@RequestMapping("/api/invites")
public class InviteValidationController {

    private final InviteApplicationService inviteService;

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(
            @RequestParam String token,
            HttpSession session
    ) {
        inviteService.validateAndStoreInviteToken(token, session);
        return ResponseEntity.noContent().build();
    }
}