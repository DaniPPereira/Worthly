package com.worthly.connections;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.connections.application.EnableBankingAuthSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnableBankingAuthSupportTest {

    @Test
    void rfc3339UtcUsesOffsetAndMicroseconds() {
        Instant instant = Instant.parse("2026-09-14T11:00:00.123456789Z");

        assertThat(EnableBankingAuthSupport.rfc3339Utc(instant)).isEqualTo("2026-09-14T11:00:00.123456+00:00");
    }

    @Test
    void consentStaysBelowMaximumByAClockSkewBuffer() {
        Instant now = Instant.parse("2026-09-14T11:00:00Z");
        Instant until = EnableBankingAuthSupport.consentValidUntil(3_600, now);

        assertThat(until).isBefore(now.plusSeconds(3_600));
        assertThat(until).isAfter(now);
    }

    @Test
    void missingMaximumFallsBackToTenDays() {
        Instant now = Instant.parse("2026-09-14T11:00:00Z");
        Instant until = EnableBankingAuthSupport.consentValidUntil(0, now);

        assertThat(until).isBeforeOrEqualTo(now.plusSeconds(EnableBankingAuthSupport.DEFAULT_CONSENT_SECONDS));
        assertThat(until).isAfter(now.plusSeconds(EnableBankingAuthSupport.DEFAULT_CONSENT_SECONDS - 400));
    }

    @Test
    void redirectMatchIsExact() {
        assertThat(EnableBankingAuthSupport.redirectRegistered(
                        List.of("http://localhost:8080/api/v1/connections/enable-banking/callback"),
                        "http://localhost:8080/api/v1/connections/enable-banking/callback"))
                .isTrue();
        assertThat(EnableBankingAuthSupport.redirectRegistered(
                        List.of("https://example.com/callback"),
                        "http://localhost:8080/api/v1/connections/enable-banking/callback"))
                .isFalse();
    }
}
