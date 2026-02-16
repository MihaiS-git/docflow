package com.brutecx.docflow_backend.audit.canonical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

/**
 * Deterministic JSON serializer for canonical audit material.
 * <p>
 * Requirements:
 * - Stable map entry ordering
 * - Stable POJO property ordering
 * - Single source of truth (shared by writers/verifiers/exports)
 */
@Component
public class CanonicalJsonService {

    private final ObjectWriter canonicalWriter;

    public CanonicalJsonService(ObjectMapper objectMapper) {
        /*
         * Determinism strategy:
         * - Records preserve declared component order.
         * - @JsonProperty defines explicit names.
         * - ORDER_MAP_ENTRIES_BY_KEYS guarantees stable Map ordering.
         *
         * This is sufficient for canonical audit metadata because:
         * - All AdminAuditMetadata implementations are records.
         * - No dynamic Maps are embedded except via explicit JSONB records.
         */
        ObjectMapper canonicalMapper = objectMapper.copy()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        this.canonicalWriter = canonicalMapper.writer();
    }

    public String toCanonicalJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return canonicalWriter.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize value deterministically for canonical audit material", e);
        }
    }
}
