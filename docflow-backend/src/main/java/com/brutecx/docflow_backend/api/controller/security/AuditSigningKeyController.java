package com.brutecx.docflow_backend.api.controller.security;

import com.brutecx.docflow_backend.domain.security.AuditSigningKey;
import com.brutecx.docflow_backend.domain.security.AuditSigningKeyPublicDTO;
import com.brutecx.docflow_backend.domain.security.AuditSigningKeyQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/security/audit-keys")
@RequiredArgsConstructor
public class AuditSigningKeyController {

    private final AuditSigningKeyQueryService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public List<AuditSigningKeyPublicDTO> list() {
        return service.listPublicKeys();
    }

    @GetMapping("/{keyId}/public")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ByteArrayResource> downloadPublicKey(
            @PathVariable String keyId
    ) {
        AuditSigningKey key = service.getRequired(keyId);

        byte[] pemBytes = key.getPublicKeyPem().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"audit-signing-key-" + keyId + ".pem\""
                )
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .body(new ByteArrayResource(pemBytes));
    }
}