package com.brutecx.docflow_backend.audit.tamper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class AuditChainService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final int CHECKPOINT_INTERVAL = 100;
    private static final int SEGMENT_INTERVAL = 10_000;

    private final AuditChainStateRepository stateRepository;
    private final AuditChainSecretProvider secretProvider;
    private final GenericAuditEventHashLocator hashLocator;
    private final boolean enabled;
    private final boolean anchorEnabled;
    private final Path anchorDir;

    private final ConcurrentHashMap<String, Object> anchorLocks = new ConcurrentHashMap<>();

    public AuditChainService(
            AuditChainStateRepository stateRepository,
            AuditChainSecretProvider secretProvider,
            GenericAuditEventHashLocator hashLocator,
            @Value("${docflow.audit.chain.enabled:true}") boolean enabled,
            @Value("${docflow.audit.chain.anchor.enabled:true}") boolean anchorEnabled,
            @Value("${docflow.audit.chain.anchor.dir:./var/audit-chain-anchors}") String anchorDir
    ) {
        this.stateRepository = stateRepository;
        this.secretProvider = secretProvider;
        this.hashLocator = hashLocator;
        this.enabled = enabled;
        this.anchorEnabled = anchorEnabled;
        this.anchorDir = Path.of(anchorDir);
    }

    public record PreparedChainHash(
            String prevHash,
            String eventHash,
            int chainVersion,
            boolean checkpoint,
            boolean segment,
            String stateKey,
            boolean stateExists,
            int eventsSinceCheckpoint,
            long eventCount,
            String checkpointHash,
            Instant checkpointAt,
            String previousCheckpointHash
    ) {
    }

    public record VerificationAnchor(
            Instant cursorTimestamp,
            UUID cursorId,
            String expectedPrevHash
    ) {
    }

    public boolean isEnabledAndConfigured() {
        String secret = secretProvider.getResolvedSecretOrEmpty();
        return enabled && secret != null && !secret.isBlank();
    }

    public String computeEventHash(
            AuditPartition partition,
            int chainVersion,
            String prevHash,
            String eventMaterial
    ) {
        if (!isEnabledAndConfigured() || chainVersion <= 0) {
            return "-";
        }

        String material =
                "v" + chainVersion +
                        "|" + partition.stream() +
                        "|" + partition.partitionValue() +
                        "|" + prevHash +
                        "|" + eventMaterial;

        String secret = secretProvider.getResolvedSecretOrEmpty();

        return AuditChainHasher.hmacSha256Hex(secret, material);
    }

    @Transactional
    public PreparedChainHash prepareHash(
            AuditPartition partition,
            String eventMaterial
    ) {
        String stateKey = partition.toStateKey();

        if (!isEnabledAndConfigured()) {
            return new PreparedChainHash(
                    "-", "-", 0, false, false,
                    stateKey, false,
                    0, 0L, null, null, null
            );
        }

        Optional<AuditChainState> stateOpt =
                stateRepository.findByStateKeyForUpdate(stateKey);

        String prevHash =
                stateOpt.map(AuditChainState::getLastEventHash)
                        .map(this::normalizeHash)
                        .orElse("-");

        int chainVersion = 1;

        String eventHash = computeEventHash(
                partition,
                chainVersion,
                prevHash,
                eventMaterial
        );

        if (stateOpt.isPresent()) {

            AuditChainState state = stateOpt.get();

            AuditChainState.AdvanceResult next =
                    state.advance(eventHash, CHECKPOINT_INTERVAL, Instant.now());

            long nextEventCount = next.eventCount();
            int nextCheckpointCount = next.eventsSinceCheckpoint();

            boolean checkpoint = next.checkpointHash() != null;
            boolean segment = (nextEventCount % SEGMENT_INTERVAL) == 0;

            return new PreparedChainHash(
                    prevHash,
                    eventHash,
                    chainVersion,
                    checkpoint,
                    segment,
                    stateKey,
                    true,
                    nextCheckpointCount,
                    nextEventCount,
                    next.checkpointHash(),
                    next.checkpointAt(),
                    normalizeNullableHash(state.getLastCheckpointHash())
            );
        }

        long nextEventCount = 1L;
        int nextCheckpointCount = 1;

        return new PreparedChainHash(
                prevHash,
                eventHash,
                chainVersion,
                false,
                false,
                stateKey,
                false,
                nextCheckpointCount,
                nextEventCount,
                null,
                null,
                null
        );
    }

    @Transactional
    public void commitHash(
            AuditPartition partition,
            PreparedChainHash prepared
    ) {
        if (!isEnabledAndConfigured()) {
            return;
        }

        Instant now = Instant.now();

        if (prepared.stateExists()) {

            int updated =
                    stateRepository.compareAndSetHash(
                            prepared.stateKey(),
                            normalizeHash(prepared.prevHash()),
                            prepared.eventHash(),
                            prepared.eventsSinceCheckpoint(),
                            prepared.eventCount(),
                            prepared.checkpointHash(),
                            prepared.checkpointAt(),
                            now
                    );

            if (updated != 1) {
                throw new IllegalStateException(
                        "Audit chain CAS update failed for stateKey=" + prepared.stateKey()
                );
            }

            if (checkpointAdvanced(
                    prepared.previousCheckpointHash(),
                    prepared.checkpointHash(),
                    prepared.checkpointAt()
            )) {
                writeAnchorSnapshot(
                        "CHECKPOINT",
                        partition,
                        prepared.stateKey(),
                        prepared.eventHash(),
                        prepared.checkpointHash(),
                        prepared.checkpointAt(),
                        now
                );
            }

            return;
        }

        int inserted =
                stateRepository.insertIfAbsent(
                        prepared.stateKey(),
                        partition.stream(),
                        partition.partitionValue(),
                        prepared.eventHash(),
                        prepared.checkpointHash(),
                        prepared.checkpointAt(),
                        prepared.eventsSinceCheckpoint(),
                        prepared.eventCount(),
                        now
                );

        if (inserted != 1) {
            throw new IllegalStateException(
                    "Concurrent bootstrap detected for stateKey=" + prepared.stateKey()
            );
        }

        writeAnchorSnapshot(
                "BOOTSTRAP",
                partition,
                prepared.stateKey(),
                prepared.eventHash(),
                prepared.checkpointHash(),
                prepared.checkpointAt(),
                now
        );

        log.warn("security_event",
                kv("schema_version", "docflow_siem_v1"),
                kv("event.category", "audit"),
                kv("event.action", "audit_chain_state_bootstrap"),
                kv("event.outcome", "success"),
                kv("audit.stream", partition.stream()),
                kv("audit.partition", partition.partitionValue()),
                kv("audit.state_key", prepared.stateKey())
        );
    }

    @Transactional(readOnly = true)
    public VerificationAnchor resolveVerificationAnchor(AuditPartition partition) {

        String stateKey = partition.toStateKey();

        Optional<AuditChainState> stateOpt =
                stateRepository.findById(stateKey);

        if (stateOpt.isEmpty()) {
            return new VerificationAnchor(null, null, "-");
        }

        AuditChainState state = stateOpt.get();

        if (!state.hasCheckpoint()) {
            return new VerificationAnchor(null, null, "-");
        }

        Optional<GenericAuditEventHashLocator.VerificationCursor> cursorOpt =
                hashLocator.findCheckpointCursor(
                        partition,
                        state.getLastCheckpointAt(),
                        state.getLastCheckpointHash()
                );

        if (cursorOpt.isEmpty()) {
            return new VerificationAnchor(null, null, "-");
        }

        GenericAuditEventHashLocator.VerificationCursor cursor =
                cursorOpt.get();

        return new VerificationAnchor(
                cursor.timestamp(),
                cursor.id(),
                normalizeHash(cursor.eventHash())
        );
    }

    private boolean checkpointAdvanced(
            String previousCheckpointHash,
            String checkpointHash,
            Instant checkpointAt
    ) {
        return checkpointAt != null &&
                checkpointHash != null &&
                !checkpointHash.isBlank() &&
                !checkpointHash.equals(previousCheckpointHash);
    }

    private void writeAnchorSnapshot(
            String reason,
            AuditPartition partition,
            String stateKey,
            String lastEventHash,
            String checkpointHash,
            Instant checkpointAt,
            Instant updatedAt
    ) {
        if (!anchorEnabled) return;

        try {
            Files.createDirectories(anchorDir);

            LocalDate date =
                    updatedAt.atZone(ZoneOffset.UTC).toLocalDate();

            Path file =
                    anchorDir.resolve("audit-chain-anchor-" + date + ".log");

            String line = String.join("\t",
                    updatedAt.toString(),
                    reason,
                    partition.stream(),
                    partition.partitionValue(),
                    stateKey,
                    nullSafe(lastEventHash),
                    nullSafe(checkpointHash),
                    checkpointAt == null ? "" : checkpointAt.toString()
            ) + System.lineSeparator();

            Object lock =
                    anchorLocks.computeIfAbsent(file.toString(), k -> new Object());

            synchronized (lock) {
                Files.writeString(
                        file,
                        line,
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND
                );
            }

        } catch (IOException ex) {

            log.error("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "audit_chain_anchor_write_failed"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", partition.stream()),
                    kv("audit.partition", partition.partitionValue()),
                    kv("message", ex.getMessage())
            );
        }
    }

    private String normalizeHash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private String normalizeNullableHash(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}