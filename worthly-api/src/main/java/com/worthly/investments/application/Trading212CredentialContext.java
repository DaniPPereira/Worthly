package com.worthly.investments.application;

public final class Trading212CredentialContext {

    public static final String LIVE_BASE = "https://live.trading212.com/api/v0";
    public static final String DEMO_BASE = "https://demo.trading212.com/api/v0";

    public record Bound(String key, String secret, String baseUrl) {
        @Override
        public String toString() {
            return "Bound[redacted]";
        }
    }

    private static final ThreadLocal<Bound> CURRENT = new ThreadLocal<>();

    private Trading212CredentialContext() {}

    public static Bound current() {
        return CURRENT.get();
    }

    public static void set(Bound bound) {
        if (bound == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(bound);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static boolean isOfficialHost(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        return baseUrl.contains("live.trading212.com") || baseUrl.contains("demo.trading212.com");
    }
}
