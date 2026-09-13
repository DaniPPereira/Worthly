package com.worthly.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.sync.application.SyncPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SyncPolicyTest {

    private final Instant now = Instant.parse("2026-09-13T12:00:00Z");

    @Test
    void activeStaleConnectionIsScheduled() {
        Instant last = now.minusSeconds(4 * 3600);
        SyncPolicy.Decision decision = SyncPolicy.decide("ACTIVE", last, now, "SUCCEEDED", null);
        assertThat(decision.shouldSync()).isTrue();
        assertThat(decision.notificationType()).isNull();
    }

    @Test
    void recentSuccessIsSkipped() {
        Instant last = now.minusSeconds(3600);
        SyncPolicy.Decision decision = SyncPolicy.decide("ACTIVE", last, now, "SUCCEEDED", null);
        assertThat(decision.shouldSync()).isFalse();
    }

    @Test
    void rateLimitedWaitsForRetry() {
        SyncPolicy.Decision decision =
                SyncPolicy.decide("ACTIVE", now.minusSeconds(10 * 3600), now, "RATE_LIMITED", now.plusSeconds(3600));
        assertThat(decision.shouldSync()).isFalse();
    }

    @Test
    void reauthDoesNotFetchAndReminds() {
        SyncPolicy.Decision decision = SyncPolicy.decide("REAUTH_REQUIRED", now.minusSeconds(10 * 3600), now, "FAILED", null);
        assertThat(decision.shouldSync()).isFalse();
        assertThat(decision.notificationType()).isEqualTo("CONNECTION_REAUTH_REQUIRED");
    }

    @Test
    void dayOldActiveRaisesRepeatedFailure() {
        Instant last = now.minusSeconds(25 * 3600);
        SyncPolicy.Decision decision = SyncPolicy.decide("ACTIVE", last, now, "FAILED", null);
        assertThat(decision.shouldSync()).isTrue();
        assertThat(decision.notificationType()).isEqualTo("SYNC_REPEATED_FAILURE");
    }
}
