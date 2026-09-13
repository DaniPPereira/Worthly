package com.worthly.banking.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public final class MoneyPresentation {

    private MoneyPresentation() {}

    public static String amount(BigDecimal value, String currency) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(minorUnits(currency), RoundingMode.HALF_UP).toPlainString();
    }

    public static String percent(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static int minorUnits(String currency) {
        if (currency == null || currency.isBlank()) {
            return 2;
        }
        try {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return digits < 0 ? 0 : digits;
        } catch (IllegalArgumentException ex) {
            return 2;
        }
    }
}
