package com.worthly.banking;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.banking.application.BankingMappings;
import org.junit.jupiter.api.Test;

class BankingMappingsTest {

    @Test
    void liquidCashIncludesCurrentSavingsAndCard() {
        assertThat(BankingMappings.includedInLiquidCash("CURRENT")).isTrue();
        assertThat(BankingMappings.includedInLiquidCash("SAVINGS")).isTrue();
        assertThat(BankingMappings.includedInLiquidCash("CARD")).isTrue();
        assertThat(BankingMappings.includedInLiquidCash("BROKERAGE")).isFalse();
        assertThat(BankingMappings.includedInLiquidCash("OTHER")).isFalse();
    }
}
