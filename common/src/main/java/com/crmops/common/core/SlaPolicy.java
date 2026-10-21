package com.crmops.common.core;

import java.time.Duration;
import java.time.Instant;

/**
 * SLA policy for support escalations. Thresholds live in one place and are
 * applied identically by the channel tier (when an escalation is created) and
 * the batch tier (when overdue escalations are flagged).
 */
public final class SlaPolicy {

    public static final String SEV1 = "SEV1";
    public static final String SEV2 = "SEV2";
    public static final String SEV3 = "SEV3";

    /** Seeded, checked-in thresholds — never accepted from a client. */
    public static Duration threshold(String severity) {
        return switch (severity) {
            case SEV1 -> Duration.ofHours(4);
            case SEV2 -> Duration.ofHours(24);
            default -> Duration.ofHours(72);
        };
    }

    public static Instant slaDueAt(String severity, Instant createdAt) {
        return createdAt.plus(threshold(severity));
    }

    public static boolean breached(String severity, Instant createdAt, Instant now) {
        return now.isAfter(slaDueAt(severity, createdAt));
    }

    private SlaPolicy() {
    }
}
