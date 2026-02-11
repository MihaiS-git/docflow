package com.brutecx.docflow_backend.api.controller;

import com.brutecx.docflow_backend.domain.user.BootstrapActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final BootstrapActivationService bootstrapActivationService;

    @PostMapping("/activate")
    public ResponseEntity<Void> activate() {
        bootstrapActivationService.activateBootstrapAdmin();
        return ResponseEntity.noContent().build();
    }
}
