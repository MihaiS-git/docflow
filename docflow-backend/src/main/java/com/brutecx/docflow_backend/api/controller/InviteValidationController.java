package com.brutecx.docflow_backend.api.controller;

import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@Profile({"dev", "prod", "test"})
@RequestMapping("/api/invites")
public class InviteValidationController {

    private final InviteApplicationService inviteService;

    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@RequestParam String token, HttpSession session) {
        inviteService.validateAndStoreInviteToken(token, session);
        return ResponseEntity.noContent().build();
    }
}
