package com.worthly.connections.application;

import com.worthly.shared.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConnectionCatalogService {

    private final EnableBankingDiscoveryService discovery;

    public ConnectionCatalogService(EnableBankingDiscoveryService discovery) {
        this.discovery = discovery;
    }

    public List<CatalogEntry> list(String country) {
        List<CatalogEntry> items = new ArrayList<>();
        items.addAll(aisEntries(country));
        items.addAll(brokerageEntries());
        return List.copyOf(items);
    }

    private List<CatalogEntry> aisEntries(String country) {
        try {
            return discovery.listV1Banks(country).stream().map(ConnectionCatalogService::fromAis).toList();
        } catch (ApiException ex) {
            return List.of();
        }
    }

    private static List<CatalogEntry> brokerageEntries() {
        ProviderProfile trading212 = ProviderProfiles.trading212();
        ProviderProfile tradeRepublic = ProviderProfiles.unavailableBroker(
                "TRADE_REPUBLIC", InstitutionBrand.TRADE_REPUBLIC, "Trade Republic investments");
        ProviderProfile revolutInvest = ProviderProfiles.unavailableBroker(
                "REVOLUT_INVEST", InstitutionBrand.REVOLUT, "Revolut Invest");
        return List.of(
                fromBroker(trading212, true, null),
                fromBroker(tradeRepublic, false, "no_official_holdings_api"),
                fromBroker(revolutInvest, false, "no_official_holdings_api"));
    }

    private static CatalogEntry fromAis(EnableBankingModels.DiscoveredBank bank) {
        ProviderProfile profile = ProviderProfiles.forAisBank(bank.name(), bank.country());
        return new CatalogEntry(
                "ais:" + bank.country() + ":" + bank.name(),
                profile.provider(),
                profile.brand(),
                bank.name(),
                bank.country(),
                profile.kind(),
                profile.authMode(),
                true,
                false,
                profile.capabilities(),
                profile.dataScope(),
                null,
                bank.logoUrl());
    }

    private static CatalogEntry fromBroker(ProviderProfile profile, boolean connectable, String unavailableReason) {
        return new CatalogEntry(
                "broker:" + profile.provider(),
                profile.provider(),
                profile.brand(),
                profile.displayName(),
                null,
                profile.kind(),
                profile.authMode(),
                connectable,
                profile.holdingsIncluded(),
                profile.capabilities(),
                profile.dataScope(),
                unavailableReason,
                null);
    }
}
