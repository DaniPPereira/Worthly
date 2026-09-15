package com.worthly.connections.application;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.investments.application.Trading212ConnectionService;
import java.util.List;

public final class ProviderProfiles {

    private static final List<ProviderCapability> AIS_CAPABILITIES =
            List.of(ProviderCapability.CASH_ACCOUNTS, ProviderCapability.TRANSACTIONS);
    private static final List<ProviderCapability> BROKERAGE_CAPABILITIES = List.of(
            ProviderCapability.CASH_ACCOUNTS,
            ProviderCapability.POSITIONS,
            ProviderCapability.INVESTMENT_HISTORY);

    private ProviderProfiles() {}

    public static ProviderProfile of(ProviderConnectionEntity connection) {
        if (Trading212ConnectionService.PROVIDER.equals(connection.getProvider())) {
            return trading212();
        }
        return forAisBank(connection.getAspspName(), connection.getAspspCountry());
    }

    public static ProviderProfile trading212() {
        return new ProviderProfile(
                Trading212ConnectionService.PROVIDER,
                InstitutionBrand.TRADING_212,
                "Trading 212",
                ProviderKind.BROKER,
                ProviderAuthMode.CREDENTIALS,
                BROKERAGE_CAPABILITIES,
                true,
                "Brokerage cash, holdings and investment history.");
    }

    public static ProviderProfile forAisBank(String name, String country) {
        InstitutionBrand brand = InstitutionBrand.fromName(name);
        String display = name == null || name.isBlank() ? "Bank" : name;
        String scope = brand.offersBrokerageSeparately()
                ? "Open Banking cash and card payments only. Holdings from this brand are a separate product and are not included."
                : "Open Banking cash, cards and transactions.";
        return new ProviderProfile(
                ConnectionService.PROVIDER,
                brand,
                display,
                ProviderKind.BANK,
                ProviderAuthMode.CONSENT,
                AIS_CAPABILITIES,
                false,
                scope);
    }

    public static ProviderProfile unavailableBroker(String provider, InstitutionBrand brand, String displayName) {
        return new ProviderProfile(
                provider,
                brand,
                displayName,
                ProviderKind.BROKER,
                ProviderAuthMode.CONSENT,
                List.of(),
                false,
                "No official brokerage API. Connecting this brand through Open Banking imports cash, not holdings.");
    }
}
