package com.worthly.connections;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.connections.application.EnableBankingDiscoveryService;
import com.worthly.connections.application.EnableBankingModels;
import org.junit.jupiter.api.Test;

class EnableBankingDiscoveryServiceTest {

    @Test
    void v1FilterKeepsSantanderPortugalAndRevolutOnly() {
        EnableBankingModels.DiscoveredBank santander =
                new EnableBankingModels.DiscoveredBank("Banco Santander Totta", "PT", null, 1);
        EnableBankingModels.DiscoveredBank revolut =
                new EnableBankingModels.DiscoveredBank("Revolut", "PT", null, 1);
        EnableBankingModels.DiscoveredBank bcp =
                new EnableBankingModels.DiscoveredBank("Millennium BCP", "PT", null, 1);
        EnableBankingModels.DiscoveredBank santanderEs =
                new EnableBankingModels.DiscoveredBank("Banco Santander Totta", "ES", null, 1);

        assertThat(EnableBankingDiscoveryService.isV1Bank(santander, "PT")).isTrue();
        assertThat(EnableBankingDiscoveryService.isV1Bank(revolut, "PT")).isTrue();
        assertThat(EnableBankingDiscoveryService.isV1Bank(bcp, "PT")).isFalse();
        assertThat(EnableBankingDiscoveryService.isV1Bank(santanderEs, "PT")).isFalse();
    }
}
