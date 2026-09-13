package com.worthly.connections.application;

import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.shared.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EnableBankingDiscoveryService {

    private static final String SANTANDER_PT = "Banco Santander Totta";

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
        return discover(normalized).stream().filter(bank -> isV1Bank(bank, normalized)).toList();
    }

    public EnableBankingModels.DiscoveredBank requireV1Bank(String name, String country) {
        String normalized = normalizeCountry(country);
        return listV1Banks(normalized).stream()
                .filter(bank -> bank.name().equals(name) && normalized.equalsIgnoreCase(bank.country()))
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
