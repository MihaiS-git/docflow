package com.brutecx.docflow_backend.api.dto.audit;

/**
 * GOLD forensic export contract.
 * All forensic export DTOs must:
 *  - expose full chain material
 *  - expose provenance fields
 *  - be stable and replay-friendly
 * This interface intentionally does not add new methods.
 * It serves as a strict marker + compile-time enforcement
 * that the DTO fully complies with BaseAuditDTO.
 */
public interface BaseAuditForensicExportDTO extends BaseAuditDTO {
}
