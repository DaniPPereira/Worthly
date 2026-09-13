package com.worthly.sync.application;

import java.time.Duration;
import java.time.Instant;

public final class SyncPolicy {

    public static final Duration STALE_AFTER = Duration.ofHours(3);
    public static final Duration FAILURE_ALERT_AFTER = Duration.ofHours(24);

    private SyncPolicy() {}

    public static Decision decide(
            String connectionStatus,
            Instant lastSuccessfulSyncAt,
            Instant now,
            String latestRunStatus,
            Instant nextRetryAt) {
        if ("REAUTH_REQUIRED".equals(connectionStatus) || "CONFIGURATION_REQUIRED".equals(connectionStatus)) {
            return new Decision(false, connectionStatus.equals("REAUTH_REQUIRED")
                    ? "CONNECTION_REAUTH_REQUIRED"
                    : "CONFIGURATION_REQUIRED");
        }
        if ("DISABLED".equals(connectionStatus) || "ERROR".equals(connectionStatus)) {
            return Decision.skip();
        }
        if (!"ACTIVE".equals(connectionStatus)) {
            return Decision.skip();
        }
        if ("RUNNING".equals(latestRunStatus) || "QUEUED".equals(latestRunStatus)) {
            return Decision.skip();
        }
        if ("RATE_LIMITED".equals(latestRunStatus) && nextRetryAt != null && nextRetryAt.isAfter(now)) {
            return Decision.skip();
        }
        boolean staleSuccess = lastSuccessfulSyncAt == null || lastSuccessfulSyncAt.isBefore(now.minus(STALE_AFTER));
        boolean failureAlert = lastSuccessfulSyncAt == null
                || lastSuccessfulSyncAt.isBefore(now.minus(FAILURE_ALERT_AFTER));
        if (!staleSuccess) {
            return Decision.skip();
        }
        return new Decision(true, failureAlert ? "SYNC_REPEATED_FAILURE" : null);
    }

    public record Decision(boolean shouldSync, String notificationType) {
        static Decision skip() {
            return new Decision(false, null);
        }
    }
}
