package com.worthly.investments.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;

public final class RateLimitHeaders {

    private RateLimitHeaders() {}

    public static Instant retryAt(HttpHeaders headers, Duration fallback) {
        Instant now = Instant.now();
        Instant fallbackAt = now.plus(fallback);
        if (headers == null) {
            return fallbackAt;
        }
        String reset = headers.getFirst("x-ratelimit-reset");
        Instant parsed = parseReset(reset, now);
        return parsed == null ? fallbackAt : parsed;
    }

    public static Instant parseReset(String reset, Instant now) {
        if (reset == null || reset.isBlank()) {
            return null;
        }
        String value = reset.trim();
        try {
            long numeric = Long.parseLong(value);
            if (numeric < 1_000_000_000L) {
                return now.plusSeconds(numeric);
            }
            if (numeric < 100_000_000_000L) {
                return Instant.ofEpochSecond(numeric);
            }
            return Instant.ofEpochMilli(numeric);
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(value);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
