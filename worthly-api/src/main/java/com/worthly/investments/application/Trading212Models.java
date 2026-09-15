package com.worthly.investments.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Trading212Models {

    private Trading212Models() {}

    public record AccountSummary(
            String accountId, String currency, BigDecimal cash, BigDecimal portfolioValue, Instant observedAt) {}

    public record Position(
            String instrumentKey,
            String ticker,
            String name,
            BigDecimal quantity,
            BigDecimal averagePrice,
            BigDecimal marketValue,
            String currency) {}

    public record HistoryEvent(
            String providerEventId,
            String eventType,
            BigDecimal amount,
            String currency,
            Instant occurredAt,
            String instrumentKey,
            BigDecimal quantity) {}

    public record HistoryPage(List<HistoryEvent> items, String nextPagePath) {}
}
