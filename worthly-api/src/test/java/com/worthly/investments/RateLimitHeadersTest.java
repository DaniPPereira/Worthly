package com.worthly.investments;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.investments.application.RateLimitHeaders;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimitHeadersTest {

    @Test
    void parsesRelativeSecondsAndEpoch() {
        Instant now = Instant.parse("2026-09-13T12:00:00Z");
        assertThat(RateLimitHeaders.parseReset("30", now)).isEqualTo(now.plusSeconds(30));
        assertThat(RateLimitHeaders.parseReset("1770000000", now)).isEqualTo(Instant.ofEpochSecond(1_770_000_000L));
        assertThat(RateLimitHeaders.parseReset("2026-09-13T12:05:00Z", now))
                .isEqualTo(Instant.parse("2026-09-13T12:05:00Z"));
    }
}
