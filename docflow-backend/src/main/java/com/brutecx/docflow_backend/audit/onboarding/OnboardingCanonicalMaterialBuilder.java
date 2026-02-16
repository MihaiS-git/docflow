package com.brutecx.docflow_backend.audit.onboarding;

import org.springframework.stereotype.Component;

@Component
public class OnboardingCanonicalMaterialBuilder {

    public static final String STREAM = "ONBOARDING";

    public String stream() {
        return STREAM;
    }

    public String buildCanonicalMaterial(OnboardingCanonicalInput in) {

        return String.join("|",
                "actorUserId=" + safe(in.actorUserId()),
                "subjectId=" + safe(in.subjectId()),
                "tenantId=" + safe(in.tenantId()),
                "inviteId=" + safe(in.inviteId()),
                "result=" + in.result().name(),
                "outcome=" + in.outcome().name(),
                "reasonCode=" + in.reasonCode(),
                "reasonDetail=" + safe(in.reasonDetail()),
                "correlationId=" + in.correlationId(),
                "timestamp=" + in.timestamp().toEpochMilli(),
                "fingerprint=" + in.eventFingerprint()
        );
    }

    private static String safe(Object v) {
        return v == null ? "-" : v.toString();
    }
}
