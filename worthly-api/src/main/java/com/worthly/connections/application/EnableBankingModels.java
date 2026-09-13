package com.worthly.connections.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class EnableBankingModels {

    private EnableBankingModels() {}

    public record DiscoveredBank(String name, String country, String logoUrl, int maximumConsentValiditySeconds) {}

    public record AuthStart(String url, Instant expiresAt) {}

    public record ProviderAccount(
            String uid,
            String identificationHash,
            String currency,
            String name,
            String cashAccountType,
            String iban) {}

    public record ProviderSession(
            String sessionId, String status, Instant expiresAt, List<ProviderAccount> accounts) {}

    public record ProviderBalance(
            String balanceType, BigDecimal amount, String currency, Instant observedAt, LocalDate referenceDate) {}

    public record ProviderTransaction(
            String transactionId,
            String entryReference,
            String creditDebitIndicator,
            String status,
            BigDecimal amount,
            String currency,
            LocalDate bookingDate,
            LocalDate valueDate,
            String description,
            String counterparty,
            String rawJson) {}

    public record TransactionPage(List<ProviderTransaction> transactions, String continuationKey) {}
}
