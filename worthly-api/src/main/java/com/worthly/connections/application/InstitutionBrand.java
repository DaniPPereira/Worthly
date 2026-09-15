package com.worthly.connections.application;

import java.util.Locale;

public enum InstitutionBrand {
    SANTANDER,
    REVOLUT,
    TRADE_REPUBLIC,
    TRADING_212,
    OTHER;

    public boolean offersBrokerageSeparately() {
        return this == REVOLUT || this == TRADE_REPUBLIC;
    }

    public static InstitutionBrand fromName(String name) {
        if (name == null || name.isBlank()) {
            return OTHER;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains("trading 212") || normalized.contains("trading212")) {
            return TRADING_212;
        }
        if (normalized.contains("trade republic") || normalized.contains("traderepublic")) {
            return TRADE_REPUBLIC;
        }
        if (normalized.startsWith("revolut") || normalized.contains(" revolut")) {
            return REVOLUT;
        }
        if (normalized.contains("santander")) {
            return SANTANDER;
        }
        return OTHER;
    }
}
