package com.worthly.connections;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.application.InstitutionBrand;
import com.worthly.connections.application.ProviderCapability;
import com.worthly.connections.application.ProviderKind;
import com.worthly.connections.application.ProviderProfile;
import com.worthly.connections.application.ProviderProfiles;
import com.worthly.investments.application.Trading212ConnectionService;
import org.junit.jupiter.api.Test;

class ProviderProfilesTest {

    @Test
    void trading212IsBrokerageWithHoldings() {
        ProviderConnectionEntity connection = new ProviderConnectionEntity();
        connection.setProvider(Trading212ConnectionService.PROVIDER);
        ProviderProfile profile = ProviderProfiles.of(connection);
        assertThat(profile.kind()).isEqualTo(ProviderKind.BROKER);
        assertThat(profile.holdingsIncluded()).isTrue();
        assertThat(profile.capabilities()).contains(ProviderCapability.POSITIONS);
        assertThat(profile.displayName()).isEqualTo("Trading 212");
    }

    @Test
    void tradeRepublicAisIsBankCashOnly() {
        ProviderProfile profile = ProviderProfiles.forAisBank("Trade Republic", "DE");
        assertThat(profile.kind()).isEqualTo(ProviderKind.BANK);
        assertThat(profile.brand()).isEqualTo(InstitutionBrand.TRADE_REPUBLIC);
        assertThat(profile.holdingsIncluded()).isFalse();
        assertThat(profile.capabilities()).doesNotContain(ProviderCapability.POSITIONS);
        assertThat(profile.dataScope()).contains("Holdings");
    }

    @Test
    void revolutAisIsNotRevolutInvest() {
        ProviderProfile profile = ProviderProfiles.forAisBank("Revolut Bank UAB", "PT");
        assertThat(profile.kind()).isEqualTo(ProviderKind.BANK);
        assertThat(profile.brand()).isEqualTo(InstitutionBrand.REVOLUT);
        assertThat(profile.holdingsIncluded()).isFalse();
    }
}
