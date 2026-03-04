package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportVerificationResultDTO;
import com.brutecx.docflow_backend.domain.audit.export.AuditExportVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit/exports")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('AUDITOR')")
public class AuditExportVerificationController {

    private final AuditExportVerificationService verificationService;

    @PostMapping(
            value = "/{snapshotId}/verify",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AuditExportVerificationResultDTO> verify(
            @PathVariable UUID snapshotId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(verificationService.verifySnapshotFile(snapshotId, file));
    }
}
