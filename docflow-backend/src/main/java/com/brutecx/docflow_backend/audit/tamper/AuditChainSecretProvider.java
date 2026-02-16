package com.brutecx.docflow_backend.audit.tamper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Supports:
 * - legacy single secret: docflow.audit.chain.secret
 * - split secret:
 * docflow.audit.chain.secret.part-a (env/docker secret)
 * docflow.audit.chain.secret.part-b-path (root-owned file mounted in container)
 * <p>
 * Recombination is performed at runtime inside the app. No infra work here.
 */
@Component
public class AuditChainSecretProvider {

    private final String legacySecret;
    private final String partA;
    private final String partBPath;

    // Lazy-loaded + cached
    private volatile String resolvedSecret;

    public AuditChainSecretProvider(
            @Value("${docflow.audit.chain.secret:}") String legacySecret,
            @Value("${docflow.audit.chain.secret.part-a:}") String partA,
            @Value("${docflow.audit.chain.secret.part-b-path:}") String partBPath
    ) {
        this.legacySecret = legacySecret;
        this.partA = partA;
        this.partBPath = partBPath;
    }

    public String getResolvedSecretOrEmpty() {
        String cached = resolvedSecret;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (resolvedSecret != null) {
                return resolvedSecret;
            }
            resolvedSecret = resolve();
            return resolvedSecret;
        }
    }

    private String resolve() {
        boolean splitConfigured = (partA != null && !partA.isBlank())
                || (partBPath != null && !partBPath.isBlank());

        if (splitConfigured) {
            if (partA == null || partA.isBlank()) {
                throw new IllegalStateException("Split audit chain secret configured but part-a is missing/blank");
            }
            if (partBPath == null || partBPath.isBlank()) {
                throw new IllegalStateException("Split audit chain secret configured but part-b-path is missing/blank");
            }

            String partB;
            try {
                partB = Files.readString(Path.of(partBPath), StandardCharsets.UTF_8).trim();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read audit chain secret part-b from path: " + partBPath, e);
            }

            if (partB.isBlank()) {
                throw new IllegalStateException("Audit chain secret part-b file is empty/blank");
            }

            // Simple concatenation. If you later want stronger composition, we can switch to HKDF.
            return (partA + partB).trim();
        }

        return legacySecret != null ? legacySecret.trim() : "";
    }
}
