package com.brutecx.docflow_backend.audit.tamper;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditChainAnchorService {

    private final AuditChainStateRepository stateRepository;
    private final ObjectMapper objectMapper;

    @Value("${docflow.audit.anchor.dir:./audit-anchors}")
    private String anchorDir;

    private Path anchorPath;

    @PostConstruct
    void init() throws IOException {

        Path dir = Paths.get(anchorDir);

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        anchorPath = dir.resolve("audit-chain-anchor.jsonl");

        if (!Files.exists(anchorPath)) {
            Files.createFile(anchorPath);
        }
    }

    public void snapshotAnchors() {
        List<AuditChainState> states = stateRepository.findAll();

        if (states.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        try (BufferedWriter writer =
                     Files.newBufferedWriter(
                             anchorPath,
                             StandardOpenOption.APPEND
                     )) {

            for (AuditChainState s : states) {
                AnchorRecord record = new AnchorRecord(
                        now,
                        s.getStream(),
                        s.getTenantId(),
                        s.getLastEventHash(),
                        s.getLastCheckpointHash(),
                        s.getLastCheckpointAt()
                );

                writer.write(objectMapper.writeValueAsString(record));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write audit anchor snapshot", e);
        }
    }

    record AnchorRecord(
            Instant timestamp,
            String stream,
            String partition,
            String lastEventHash,
            String lastCheckpointHash,
            Instant lastCheckpointAt
    ) {}
}