package com.worthly.connections.application;

import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.shared.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EnableBankingDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(EnableBankingDiscoveryService.class);
    private static final String SANTANDER_PT = "Banco Santander Totta";
    private static final String ALL_COUNTRIES = "*";

    private final EnableBankingGateway gateway;
    private final WorthlyProperties.EnableBanking properties;
    private final Map<String, CachedDiscovery> cache = new ConcurrentHashMap<>();

    public EnableBankingDiscoveryService(EnableBankingGateway gateway, WorthlyProperties properties) {
        this.gateway = gateway;
        this.properties = properties.getEnableBanking();
    }

    public List<EnableBankingModels.DiscoveredBank> listV1Banks(String country) {
        requireConfigured();
        String normalized = normalizeCountry(country);
        List<EnableBankingModels.DiscoveredBank> discovered = discover(normalized);
        List<EnableBankingModels.DiscoveredBank> v1 =
                discovered.stream().filter(bank -> isV1Bank(bank, normalized)).toList();
        if (!v1.isEmpty()) {
            return v1;
        }
        List<EnableBankingModels.DiscoveredBank> mocks =
                discovered.stream().filter(EnableBankingDiscoveryService::isMockBank).toList();
        if (mocks.isEmpty()) {
            mocks = discover(ALL_COUNTRIES).stream()
                    .filter(EnableBankingDiscoveryService::isMockBank)
                    .toList();
        }
        log.info(
                "No Santander/Revolut ASPSPs for {}; Enable Banking returned {} banks, {} mock/sandbox connectable",
                normalized,
                discovered.size(),
                mocks.size());
        return mocks;
    }

    public EnableBankingModels.DiscoveredBank requireV1Bank(String name, String country) {
        String normalized = normalizeCountry(country);
        return listV1Banks(normalized).stream()
                .filter(bank -> name.equals(bank.name()))
                .filter(bank -> bank.country() == null
                        || bank.country().isBlank()
                        || normalized.equalsIgnoreCase(bank.country())
                        || isMockBank(bank))
                .findFirst()
                .orElseThrow(() -> ApiException.of(HttpStatus.BAD_REQUEST, "unsupported_bank"));
    }

    public static boolean isV1Bank(EnableBankingModels.DiscoveredBank bank, String country) {
        if (bank == null || bank.name() == null || bank.country() == null) {
            return false;
        }
        if (!country.equalsIgnoreCase(bank.country())) {
            return false;
        }
        if (SANTANDER_PT.equals(bank.name()) && "PT".equalsIgnoreCase(country)) {
            return true;
        }
        return bank.name().startsWith("Revolut");
    }

    public static boolean isMockBank(EnableBankingModels.DiscoveredBank bank) {
        if (bank == null || bank.name() == null) {
            return false;
        }
        String name = bank.name().toLowerCase(Locale.ROOT);
        return name.contains("mock") || name.contains("sandbox");
    }

    private List<EnableBankingModels.DiscoveredBank> discover(String country) {
        Instant now = Instant.now();
        CachedDiscovery cached = cache.get(country);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.banks();
        }
        try {
            List<EnableBankingModels.DiscoveredBank> banks = gateway.listAspsps(country);
            cache.put(country, new CachedDiscovery(banks, now.plus(properties.getDiscoveryTtl())));
            return banks;
        } catch (EnableBankingGateway.ProviderException | EnableBankingGateway.RateLimitedException ex) {
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "provider_error");
        }
    }

    private void requireConfigured() {
        if (!gateway.configured()) {
            throw ApiException.of(HttpStatus.SERVICE_UNAVAILABLE, "configuration_required");
        }
    }

    static String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "PT";
        }
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private record CachedDiscovery(List<EnableBankingModels.DiscoveredBank> banks, Instant expiresAt) {}
}
