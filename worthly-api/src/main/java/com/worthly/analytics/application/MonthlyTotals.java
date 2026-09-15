package com.worthly.analytics.application;

import com.worthly.banking.application.MoneyPresentation;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MonthlyTotals {

    private MonthlyTotals() {}

    public static List<Row> of(List<TransactionEntity> bookedInMonth) {
        return of(bookedInMonth, Set.of());
    }

    /**
     * {@code skipAccountIds} is the brokerage cash account. Bank→T212 is recorded
     * twice (bank debit and T212 deposit); Funded must count that cash once.
     */
    public static List<Row> of(List<TransactionEntity> bookedInMonth, Set<UUID> skipAccountIds) {
        Map<String, Acc> byCurrency = new HashMap<>();
        for (TransactionEntity tx : bookedInMonth) {
            if (!"BOOKED".equals(tx.getLifecycleStatus())) {
                continue;
            }
            Acc acc = byCurrency.computeIfAbsent(tx.getCurrency(), ignored -> new Acc());
            String type = tx.getEconomicType();
            if ("INCOME".equals(type)) {
                acc.income = acc.income.add(tx.getAmount());
            } else if ("EXPENSE".equals(type) || "FEE".equals(type)) {
                acc.expenses = acc.expenses.add(tx.getAmount());
            } else if ("INVESTMENT_FUNDING".equals(type) || "INVESTMENT_WITHDRAWAL".equals(type)) {
                if (skipAccountIds.contains(tx.getAccountId())) {
                    continue;
                }
                if ("INVESTMENT_FUNDING".equals(type)) {
                    acc.funding = acc.funding.add(tx.getAmount());
                } else {
                    acc.withdrawal = acc.withdrawal.add(tx.getAmount());
                }
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Acc> entry : byCurrency.entrySet()) {
            String currency = entry.getKey();
            Acc acc = entry.getValue();
            BigDecimal invested = acc.funding.subtract(acc.withdrawal);
            BigDecimal savings = acc.income.subtract(acc.expenses);
            String rate = null;
            String reason = null;
            if (acc.income.compareTo(BigDecimal.ZERO) > 0) {
                rate = MoneyPresentation.percent(
                        savings.multiply(BigDecimal.valueOf(100)).divide(acc.income, 8, RoundingMode.HALF_UP));
            } else {
                reason = "NO_POSITIVE_INCOME";
            }
            rows.add(new Row(
                    currency,
                    MoneyPresentation.amount(acc.income, currency),
                    MoneyPresentation.amount(acc.expenses, currency),
                    MoneyPresentation.amount(invested, currency),
                    MoneyPresentation.amount(savings, currency),
                    rate,
                    reason));
        }
        rows.sort(Comparator.comparing(Row::currency));
        return rows;
    }

    public record Row(
            String currency,
            String income,
            String expenses,
            String invested,
            String savings,
            String savingsRate,
            String savingsRateReason) {}

    private static final class Acc {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expenses = BigDecimal.ZERO;
        private BigDecimal funding = BigDecimal.ZERO;
        private BigDecimal withdrawal = BigDecimal.ZERO;
    }
}
