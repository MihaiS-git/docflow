package com.brutecx.docflow_backend.domain.audit.retention;

import com.brutecx.docflow_backend.audit.tamper.AuditChainState;
import com.brutecx.docflow_backend.audit.tamper.AuditChainStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RetentionCheckpointGuard {

    private final AuditChainStateRepository stateRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Prevents retention job from deleting rows that
     * still belong to the active checkpoint window.
     */
    public boolean isDeletionSafe(
            String tableName,
            String timestampColumn,
            Object cutoff
    ) {

        List<AuditChainState> states = stateRepository.findAll();

        for (AuditChainState state : states) {

            String checkpointHash = state.getLastCheckpointHash();

            if (checkpointHash == null) {
                continue;
            }

            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM %s
                    WHERE event_hash = ?
                      AND %s < ?
                    """.formatted(tableName, timestampColumn),
                    Integer.class,
                    checkpointHash,
                    cutoff
            );

            if (count != null && count > 0) {
                return false;
            }
        }

        return true;
    }
}