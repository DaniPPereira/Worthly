package com.worthly.investments.application;

import com.worthly.banking.application.MoneyPresentation;
import com.worthly.investments.adapter.out.persistence.InvestmentAccountEntity;
import com.worthly.investments.adapter.out.persistence.InvestmentAccountRepository;
import com.worthly.investments.adapter.out.persistence.PositionSnapshotEntity;
import com.worthly.investments.adapter.out.persistence.PositionSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentQueryService {

    private final InvestmentAccountRepository accounts;
    private final PositionSnapshotRepository positions;
    private final Clock clock;

    public InvestmentQueryService(
            InvestmentAccountRepository accounts, PositionSnapshotRepository positions, Clock clock) {
        this.accounts = accounts;
        this.positions = positions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID userId) {
        List<InvestmentAccountEntity> owned = accounts.findByUserId(userId);
        if (owned.isEmpty()) {
            return new Summary(List.of(), clock.instant());
        }
        List<PositionSnapshotEntity> latest = positions.findLatestByInvestmentAccountIdIn(
                owned.stream().map(InvestmentAccountEntity::getId).toList());
        Map<String, Totals> byCurrency = new TreeMap<>();
        Instant observedAt = null;
        for (PositionSnapshotEntity snapshot : latest) {
            Totals totals = byCurrency.computeIfAbsent(snapshot.getCurrency(), ignored -> new Totals());
            if (Trading212SyncService.CASH_KEY.equals(snapshot.getInstrumentKey())) {
                totals.cash = totals.cash.add(nullToZero(snapshot.getMarketValue()));
            } else if (Trading212SyncService.PORTFOLIO_KEY.equals(snapshot.getInstrumentKey())) {
                totals.portfolio = totals.portfolio.add(nullToZero(snapshot.getMarketValue()));
            }
            if (observedAt == null || snapshot.getObservedAt().isAfter(observedAt)) {
                observedAt = snapshot.getObservedAt();
            }
        }
        List<CurrencyTotal> rows = new ArrayList<>();
        for (Map.Entry<String, Totals> entry : byCurrency.entrySet()) {
            rows.add(new CurrencyTotal(
                    entry.getKey(),
                    MoneyPresentation.amount(entry.getValue().cash, entry.getKey()),
                    MoneyPresentation.amount(entry.getValue().portfolio, entry.getKey())));
        }
        rows.sort(Comparator.comparing(CurrencyTotal::currency));
        return new Summary(rows, observedAt == null ? clock.instant() : observedAt);
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> investmentValueByCurrency(UUID userId) {
        Summary summary = summary(userId);
        Map<String, BigDecimal> values = new TreeMap<>();
        for (CurrencyTotal row : summary.totalsByCurrency()) {
            values.put(
                    row.currency(),
                    new BigDecimal(row.cash()).add(new BigDecimal(row.portfolioValue())));
        }
        return values;
    }

    @Transactional(readOnly = true)
    public List<PositionView> positions(UUID userId) {
        List<InvestmentAccountEntity> owned = accounts.findByUserId(userId);
        if (owned.isEmpty()) {
            return List.of();
        }
        return positions.findLatestByInvestmentAccountIdIn(
                        owned.stream().map(InvestmentAccountEntity::getId).toList())
                .stream()
                .filter(snapshot -> !snapshot.getInstrumentKey().startsWith("__"))
                .sorted(Comparator.comparing(PositionSnapshotEntity::getInstrumentKey))
                .map(snapshot -> new PositionView(
                        snapshot.getInstrumentKey(),
                        snapshot.getTicker(),
                        snapshot.getQuantity() == null
                                ? "0"
                                : snapshot.getQuantity().stripTrailingZeros().toPlainString(),
                        snapshot.getMarketValue() == null
                                ? null
                                : new MoneyView(
                                        MoneyPresentation.amount(snapshot.getMarketValue(), snapshot.getCurrency()),
                                        snapshot.getCurrency()),
                        snapshot.getObservedAt()))
                .toList();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record Summary(List<CurrencyTotal> totalsByCurrency, Instant observedAt) {}

    public record CurrencyTotal(String currency, String cash, String portfolioValue) {}

    public record PositionView(
            String instrumentKey, String ticker, String quantity, MoneyView marketValue, Instant observedAt) {}

    public record MoneyView(String amount, String currency) {}

    private static final class Totals {
        private BigDecimal cash = BigDecimal.ZERO;
        private BigDecimal portfolio = BigDecimal.ZERO;
    }
}
