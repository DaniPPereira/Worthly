package com.worthly.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TotpCodesTest {

    @Test
    void roundTripSecretAndAcceptsCurrentWindow() {
        String secret = TotpCodes.newSecret();
        assertThat(secret).isNotBlank();
        String code = TotpCodes.code(secret);
        assertThat(code).hasSize(6);
        assertThat(TotpCodes.matchingCounter(secret, code, null)).isPresent();
        assertThat(TotpCodes.matchingCounter(secret, "000000", null)).isEmpty();
    }

    @Test
    void rejectsReplayOfTheSameCounter() {
        String secret = TotpCodes.newSecret();
        long counter = java.time.Instant.now().getEpochSecond() / TotpCodes.PERIOD_SECONDS;
        String code = TotpCodes.codeAt(secret, counter);
        assertThat(TotpCodes.matchingCounter(secret, code, counter)).isEmpty();
    }

    @Test
    void recoveryNormalizationStripsDashes() {
        assertThat(TotpCodes.normalizeRecovery("abCD-1234")).isEqualTo("ABCD1234");
    }
}
