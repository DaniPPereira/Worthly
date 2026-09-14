package com.worthly.connections.application;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class EnableBankingAuthSupport {

    public static final long DEFAULT_CONSENT_SECONDS = Duration.ofDays(10).toSeconds();
    private static final long MAX_BUFFER_SECONDS = 300;
    private static final DateTimeFormatter RFC3339_UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'")
            .withZone(ZoneOffset.UTC);

    private EnableBankingAuthSupport() {}

    public static Instant consentValidUntil(int maximumConsentValiditySeconds, Instant now) {
        long max = maximumConsentValiditySeconds > 0 ? maximumConsentValiditySeconds : DEFAULT_CONSENT_SECONDS;
        long buffer = Math.min(MAX_BUFFER_SECONDS, Math.max(1, max / 20));
        return now.truncatedTo(ChronoUnit.SECONDS).plusSeconds(Math.max(1, max - buffer));
    }

    public static String rfc3339Utc(Instant instant) {
        return RFC3339_UTC.format(instant.truncatedTo(ChronoUnit.MICROS));
    }

    public static boolean redirectRegistered(List<String> registered, String callback) {
        if (registered == null || callback == null || callback.isBlank()) {
            return false;
        }
        return registered.stream().anyMatch(callback::equals);
    }
}
