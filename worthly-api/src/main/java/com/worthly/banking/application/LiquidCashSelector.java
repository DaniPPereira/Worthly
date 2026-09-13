package com.worthly.banking.application;

import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class LiquidCashSelector {

    private LiquidCashSelector() {}

    public static Optional<BalanceSnapshotEntity> select(
            FinancialAccountEntity account, List<BalanceSnapshotEntity> snapshots) {
        if (account == null
                || !account.isActive()
                || !BankingMappings.includedInLiquidCash(account.getType())
                || snapshots == null
                || snapshots.isEmpty()) {
            return Optional.empty();
        }
        return snapshots.stream()
                .min(Comparator.comparingInt(LiquidCashSelector::rank)
                        .thenComparing(BalanceSnapshotEntity::getObservedAt, Comparator.reverseOrder()));
    }

    public static String selectedType(FinancialAccountEntity account, List<BalanceSnapshotEntity> snapshots) {
        return select(account, snapshots).map(BalanceSnapshotEntity::getBalanceType).orElse(null);
    }

    static int rank(BalanceSnapshotEntity snapshot) {
        if (BankingMappings.isPreferredLiquidBalance(snapshot.getBalanceType())) {
            return 0;
        }
        if (BankingMappings.isBookedLiquidBalance(snapshot.getBalanceType())) {
            return 1;
        }
        return 9;
    }
}
