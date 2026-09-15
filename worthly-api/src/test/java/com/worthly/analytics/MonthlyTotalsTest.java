package com.worthly.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.analytics.application.MonthlyTotals;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonthlyTotalsTest {

    @Test
    void savingsRateIsNullWithoutPositiveIncome() {
        TransactionEntity expense = tx("EXPENSE", "12.50");
        List<MonthlyTotals.Row> rows = MonthlyTotals.of(List.of(expense));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).income()).isEqualTo("0.00");
        assertThat(rows.get(0).expenses()).isEqualTo("12.50");
        assertThat(rows.get(0).savings()).isEqualTo("-12.50");
        assertThat(rows.get(0).savingsRate()).isNull();
        assertThat(rows.get(0).savingsRateReason()).isEqualTo("NO_POSITIVE_INCOME");
    }

    @Test
    void savingsRateUsesIncomeMinusExpenses() {
        TransactionEntity income = tx("INCOME", "1000.00");
        TransactionEntity expense = tx("EXPENSE", "250.00");
        TransactionEntity fee = tx("FEE", "2.50");
        TransactionEntity transfer = tx("INTERNAL_TRANSFER", "50.00");
        List<MonthlyTotals.Row> rows = MonthlyTotals.of(List.of(income, expense, fee, transfer));
        assertThat(rows.get(0).income()).isEqualTo("1000.00");
        assertThat(rows.get(0).expenses()).isEqualTo("252.50");
        assertThat(rows.get(0).savings()).isEqualTo("747.50");
        assertThat(rows.get(0).savingsRate()).isEqualTo("74.75");
        assertThat(rows.get(0).savingsRateReason()).isNull();
        assertThat(rows.get(0).invested()).isEqualTo("0.00");
    }

    @Test
    void investedCountsBankFundingOnceWhenBrokerageMirrorsTheDeposit() {
        UUID bank = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID brokerage = UUID.fromString("22222222-2222-2222-2222-222222222222");
        TransactionEntity sent = tx("INVESTMENT_FUNDING", "911.35");
        sent.setAccountId(bank);
        TransactionEntity received = tx("INVESTMENT_FUNDING", "911.35");
        received.setAccountId(brokerage);
        List<MonthlyTotals.Row> doubled = MonthlyTotals.of(List.of(sent, received));
        assertThat(doubled.get(0).invested()).isEqualTo("1822.70");
        List<MonthlyTotals.Row> once = MonthlyTotals.of(List.of(sent, received), Set.of(brokerage));
        assertThat(once.get(0).invested()).isEqualTo("911.35");
    }

    private static TransactionEntity tx(String economicType, String amount) {
        TransactionEntity entity = new TransactionEntity();
        entity.setAccountId(UUID.randomUUID());
        entity.setExternalTransactionId(UUID.randomUUID());
        entity.setDirection("DEBIT");
        entity.setLifecycleStatus("BOOKED");
        entity.setEconomicType(economicType);
        entity.setAmount(new BigDecimal(amount));
        entity.setCurrency("EUR");
        entity.setReportingAt(Instant.parse("2026-01-15T00:00:00Z"));
        entity.setCategorizationSource("RULE");
        return entity;
    }
}
