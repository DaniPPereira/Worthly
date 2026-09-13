package com.worthly.analytics.application;

import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotEntity;
import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotRepository;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.application.LiquidCashSelector;
import com.worthly.banking.application.MoneyPresentation;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.identity.application.OwnerService;
import com.worthly.identity.domain.Owner;
import com.worthly.shared.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private final FinancialAccountRepository accounts;
    private final BalanceSnapshotRepository balances;
    private final TransactionRepository transactions;
    private final OwnerService ownerService;
    private final Clock clock;

    public AnalyticsService(
            FinancialAccountRepository accounts,
            BalanceSnapshotRepository balances,
            TransactionRepository transactions,
            OwnerService ownerService,
            Clock clock) {
        this.accounts = accounts;
        this.balances = balances;
        this.transactions = transactions;
        this.ownerService = ownerService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WealthSummary summary(UUID userId) {
        Owner owner = ownerService.require(userId);
        Map<String, BigDecimal> liquid = new TreeMap<>();
        for (FinancialAccountEntity account : accounts.findByUserIdOrderByDisplayNameAsc(userId)) {
            List<BalanceSnapshotEntity> snapshots = balances.findByAccountIdOrderByObservedAtDesc(account.getId());
            LiquidCashSelector.select(account, snapshots).ifPresent(snapshot -> liquid.merge(
                    snapshot.getCurrency(), snapshot.getAmount(), BigDecimal::add));
        }
        List<WealthCurrency> totals = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : liquid.entrySet()) {
            String currency = entry.getKey();
            BigDecimal cash = entry.getValue();
            BigDecimal investment = BigDecimal.ZERO;
            totals.add(new WealthCurrency(
                    currency,
                    MoneyPresentation.amount(cash, currency),
                    MoneyPresentation.amount(investment, currency),
                    MoneyPresentation.amount(cash.add(investment), currency)));
        }
        return new WealthSummary(clock.instant(), owner.reportingTimezone(), totals);
    }

    @Transactional(readOnly = true)
    public MonthlyAnalytics monthly(UUID userId, String month) {
        YearMonth yearMonth = parseMonth(month);
        Owner owner = ownerService.require(userId);
        ZoneId zone = ZoneId.of(owner.reportingTimezone());
        Instant start = yearMonth.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        List<UUID> accountIds = accounts.findByUserIdOrderByDisplayNameAsc(userId).stream()
                .map(FinancialAccountEntity::getId)
                .toList();
        List<TransactionEntity> inMonth = new ArrayList<>();
        if (!accountIds.isEmpty()) {
            for (TransactionEntity tx : transactions.findByAccountIdInAndLifecycleStatus(accountIds, "BOOKED")) {
                Instant at = tx.getReportingAt();
                if (!at.isBefore(start) && at.isBefore(end)) {
                    inMonth.add(tx);
                }
            }
        }
        return new MonthlyAnalytics(yearMonth.toString(), owner.reportingTimezone(), MonthlyTotals.of(inMonth));
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_month");
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_month");
        }
    }

    public record WealthSummary(Instant asOf, String timezone, List<WealthCurrency> totalsByCurrency) {}

    public record WealthCurrency(String currency, String liquidCash, String investmentValue, String netWorth) {}

    public record MonthlyAnalytics(String month, String timezone, List<MonthlyTotals.Row> totalsByCurrency) {}
}
