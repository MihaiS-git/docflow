package com.brutecx.docflow_backend.logging;

public enum InfraEventOutcome {
    SUCCESS,
    FAILURE,
    BLOCKED,
    DEGRADED,
    RETRYING;

    public String value() {
        return name().toLowerCase();
    }
}