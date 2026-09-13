package com.worthly.investments.application;

import java.util.List;

public interface Trading212Gateway {

    boolean credentialsPresent();

    Trading212Models.AccountSummary accountSummary();

    List<Trading212Models.Position> positions();

    Trading212Models.HistoryPage transactions(String nextPagePath);

    Trading212Models.HistoryPage dividends(String nextPagePath);

    class RateLimitedException extends RuntimeException {
        private final java.time.Instant retryAt;

        public RateLimitedException(java.time.Instant retryAt) {
            super("provider_rate_limited");
            this.retryAt = retryAt;
        }

        public java.time.Instant retryAt() {
            return retryAt;
        }
    }

    class ProviderException extends RuntimeException {
        private final int status;
        private final String code;

        public ProviderException(int status, String code) {
            super(code);
            this.status = status;
            this.code = code;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }
    }
}
