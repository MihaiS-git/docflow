package com.brutecx.docflow_backend.api.controller;

import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@Profile({"dev", "prod"})
@RequestMapping("/api/admin/invites")
public class InviteController {

    private final InviteApplicationService inviteService;

    @PostMapping
    public ResponseEntity<Void> invite(@RequestParam String email) {
        inviteService.createAndSendInvite(email);
        return ResponseEntity.noContent().build();
    }

}
