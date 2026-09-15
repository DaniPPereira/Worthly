package com.worthly.connections.application;

import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.shared.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
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
        log.info(
                "Enable Banking ASPSPs for {}: {}",
                normalized,
                discovered.stream().map(EnableBankingModels.DiscoveredBank::name).toList());
        List<EnableBankingModels.DiscoveredBank> v1 =
                discovered.stream().filter(bank -> isV1Bank(bank, normalized)).toList();
        v1 = mergeTradeRepublicFromHomeMarket(normalized, v1);
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
                "No v1 AIS banks for {}; Enable Banking returned {} banks, {} mock/sandbox connectable",
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
        String name = bank.name().toLowerCase(Locale.ROOT);
        if ("PT".equalsIgnoreCase(country) && name.contains("santander")) {
            return true;
        }
        if (InstitutionBrand.fromName(bank.name()) == InstitutionBrand.TRADE_REPUBLIC) {
            return true;
        }
        return name.startsWith("revolut") || name.contains(" revolut");
    }

    public static boolean isMockBank(EnableBankingModels.DiscoveredBank bank) {
        if (bank == null || bank.name() == null) {
            return false;
        }
        String name = bank.name().toLowerCase(Locale.ROOT);
        return name.contains("mock") || name.contains("sandbox");
    }

    private List<EnableBankingModels.DiscoveredBank> mergeTradeRepublicFromHomeMarket(
            String country, List<EnableBankingModels.DiscoveredBank> v1) {
        if (!"PT".equalsIgnoreCase(country)) {
            return v1;
        }
        boolean already = v1.stream()
                .anyMatch(bank -> InstitutionBrand.fromName(bank.name()) == InstitutionBrand.TRADE_REPUBLIC);
        if (already) {
            return v1;
        }
        List<EnableBankingModels.DiscoveredBank> extra = discoverOptional("DE").stream()
                .filter(bank -> InstitutionBrand.fromName(bank.name()) == InstitutionBrand.TRADE_REPUBLIC)
                .toList();
        if (extra.isEmpty()) {
            return v1;
        }
        ArrayList<EnableBankingModels.DiscoveredBank> merged = new ArrayList<>(v1);
        merged.addAll(extra);
        return List.copyOf(merged);
    }

    private List<EnableBankingModels.DiscoveredBank> discoverOptional(String country) {
        try {
            return discover(country);
        } catch (RuntimeException ex) {
            log.info("Optional ASPSP discovery for {} skipped: {}", country, ex.getMessage());
            return List.of();
        }
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
