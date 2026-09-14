package com.worthly.connections.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EnableBankingGateway {

    boolean configured();

    List<EnableBankingModels.DiscoveredBank> listAspsps(String country);

    default Optional<ApplicationInfo> application() {
        return Optional.empty();
    }

    EnableBankingModels.AuthStart startAuthorization(
            String aspspName, String country, String state, Instant validUntil);

    EnableBankingModels.ProviderSession createSession(String code);

    EnableBankingModels.ProviderSession getSession(String sessionId);

    List<EnableBankingModels.ProviderBalance> listBalances(String accountUid);

    EnableBankingModels.TransactionPage listTransactions(
            String accountUid, LocalDate dateFrom, LocalDate dateTo, String continuationKey);

    void deleteSession(String sessionId);

    class RateLimitedException extends RuntimeException {
        private final Instant retryAt;

        public RateLimitedException(Instant retryAt) {
            super("aspsp_rate_limited");
            this.retryAt = retryAt;
        }

        public Instant retryAt() {
            return retryAt;
        }
    }

    class ProviderException extends RuntimeException {
        private final String code;
        private final int status;

        public ProviderException(int status, String code) {
            super(code);
            this.status = status;
            this.code = code;
        }

        public String code() {
            return code;
        }

        public int status() {
            return status;
        }
    }

    static Instant retryAtFrom(Duration fallback, String retryAfterHeader) {
        if (retryAfterHeader != null && !retryAfterHeader.isBlank()) {
            try {
                return Instant.now().plusSeconds(Long.parseLong(retryAfterHeader.trim()));
            } catch (NumberFormatException ignored) {
                // RFC date Retry-After is ignored; fallback applies.
            }
        }
        return Instant.now().plus(fallback);
    }

    record ApplicationInfo(List<String> redirectUrls, String environment) {}
}
