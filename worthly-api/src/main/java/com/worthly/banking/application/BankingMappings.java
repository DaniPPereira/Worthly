package com.worthly.banking.application;

import java.util.Locale;

public final class BankingMappings {

    private BankingMappings() {}

    public static String accountType(String cashAccountType) {
        if (cashAccountType == null || cashAccountType.isBlank()) {
            return "OTHER";
        }
        return switch (cashAccountType.toUpperCase(Locale.ROOT)) {
            case "CACC" -> "CURRENT";
            case "SVGS" -> "SAVINGS";
            case "CARD" -> "CARD";
            default -> "OTHER";
        };
    }

    public static boolean includedInLiquidCash(String type) {
        return "CURRENT".equals(type) || "SAVINGS".equals(type) || "CARD".equals(type);
    }

    public static boolean includedInNetWorth(String type) {
        return includedInLiquidCash(type);
    }

    public static String direction(String creditDebitIndicator) {
        if (creditDebitIndicator != null) {
            String value = creditDebitIndicator.toUpperCase(Locale.ROOT);
            if ("CRDT".equals(value) || "CREDIT".equals(value)) {
                return "CREDIT";
            }
        }
        return "DEBIT";
    }

    public static String lifecycle(String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return "UNKNOWN";
        }
        return switch (providerStatus.toUpperCase(Locale.ROOT)) {
            case "BOOK", "BOOKED" -> "BOOKED";
            case "PDNG", "PENDING" -> "PENDING";
            default -> "UNKNOWN";
        };
    }

    public static String maskIdentifier(String iban) {
        if (iban == null || iban.isBlank()) {
            return null;
        }
        String compact = iban.replace(" ", "");
        if (compact.length() <= 4) {
            return "****";
        }
        return "****" + compact.substring(compact.length() - 4);
    }

    public static String connectionStatus(String sessionStatus) {
        if (sessionStatus == null || sessionStatus.isBlank()) {
            return "ACTIVE";
        }
        return switch (sessionStatus.toUpperCase(Locale.ROOT)) {
            case "AUTHORIZED" -> "ACTIVE";
            case "EXPIRED", "REVOKED", "INVALID" -> "REAUTH_REQUIRED";
            case "CLOSED" -> "DISABLED";
            default -> "ERROR";
        };
    }

    public static boolean isPreferredLiquidBalance(String balanceType) {
        if (balanceType == null) {
            return false;
        }
        String normalized = balanceType.toLowerCase(Locale.ROOT).replace("_", "");
        return normalized.contains("interimavailable") || normalized.equals("available");
    }

    public static boolean isBookedLiquidBalance(String balanceType) {
        if (balanceType == null) {
            return false;
        }
        String normalized = balanceType.toLowerCase(Locale.ROOT).replace("_", "");
        return normalized.contains("closingbooked") || normalized.equals("booked");
    }
}
