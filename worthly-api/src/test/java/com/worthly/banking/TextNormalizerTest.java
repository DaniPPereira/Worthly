package com.worthly.banking;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.banking.application.TextNormalizer;
import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void trimsLowercasesAndAppliesNfkc() {
        assertThat(TextNormalizer.normalize("  Café  ")).isEqualTo("café");
        assertThat(TextNormalizer.normalize("NETFLIX")).isEqualTo("netflix");
        assertThat(TextNormalizer.normalize(null)).isEmpty();
        assertThat(TextNormalizer.normalize("   ")).isEmpty();
        assertThat(TextNormalizer.normalize("e\u0301")).isEqualTo(TextNormalizer.normalize("é"));
    }
}
