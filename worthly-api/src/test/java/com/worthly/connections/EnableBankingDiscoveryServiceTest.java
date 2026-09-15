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

        EnableBankingModels.DiscoveredBank santanderBrand =
                new EnableBankingModels.DiscoveredBank("Santander Totta", "PT", null, 1);
        EnableBankingModels.DiscoveredBank revolutUab =
                new EnableBankingModels.DiscoveredBank("Revolut Bank UAB", "PT", null, 1);

        EnableBankingModels.DiscoveredBank tradeRepublic =
                new EnableBankingModels.DiscoveredBank("Trade Republic", "DE", null, 1);
        EnableBankingModels.DiscoveredBank tradeRepublicPt =
                new EnableBankingModels.DiscoveredBank("Trade Republic", "PT", null, 1);

        assertThat(EnableBankingDiscoveryService.isV1Bank(santander, "PT")).isTrue();
        assertThat(EnableBankingDiscoveryService.isV1Bank(santanderBrand, "PT")).isTrue();
        assertThat(EnableBankingDiscoveryService.isV1Bank(revolut, "PT")).isTrue();
        assertThat(EnableBankingDiscoveryService.isV1Bank(revolutUab, "PT")).isTrue();
        assertThat(EnableBankingDiscoveryService.isV1Bank(tradeRepublicPt, "PT")).isTrue();
        assertThat(EnableBankingDiscoveryService.isV1Bank(tradeRepublic, "DE")).isTrue();
        assertThat(EnableBankingDiscoveryService.isV1Bank(tradeRepublic, "PT")).isFalse();
        assertThat(EnableBankingDiscoveryService.isV1Bank(bcp, "PT")).isFalse();
        assertThat(EnableBankingDiscoveryService.isV1Bank(santanderEs, "PT")).isFalse();
        assertThat(EnableBankingDiscoveryService.isMockBank(
                        new EnableBankingModels.DiscoveredBank("Mock ASPSP", "FI", null, 1)))
                .isTrue();
    }
}
