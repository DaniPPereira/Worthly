package com.worthly.banking.application;

import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotEntity;
import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotRepository;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.connections.application.EnableBankingGateway;
import com.worthly.connections.application.EnableBankingModels;
import com.worthly.infrastructure.security.TokenHashes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BalanceSnapshotImporter {

    private static final Logger log = LoggerFactory.getLogger(BalanceSnapshotImporter.class);

    private final EnableBankingGateway gateway;
    private final BalanceSnapshotRepository balances;

    public BalanceSnapshotImporter(EnableBankingGateway gateway, BalanceSnapshotRepository balances) {
        this.gateway = gateway;
        this.balances = balances;
    }

    public int importBalances(FinancialAccountEntity account) {
        List<EnableBankingModels.ProviderBalance> providerBalances =
                gateway.listBalances(account.getProviderAccountAlias());
        int imported = 0;
        for (EnableBankingModels.ProviderBalance balance : providerBalances) {
            String sourceHash = TokenHashes.sha256(String.join(
                    "|",
                    nullToEmpty(balance.balanceType()),
                    balance.amount() == null ? "0" : balance.amount().toPlainString(),
                    nullToEmpty(balance.currency()),
                    String.valueOf(balance.referenceDate())));
            if (balances.findByAccountIdAndSourceHash(account.getId(), sourceHash).isPresent()) {
                continue;
            }
            BalanceSnapshotEntity entity = new BalanceSnapshotEntity();
            entity.setAccountId(account.getId());
            entity.setBalanceType(balance.balanceType() == null ? "unknown" : balance.balanceType());
            entity.setAmount(balance.amount() == null ? BigDecimal.ZERO : balance.amount());
            entity.setCurrency(balance.currency() == null ? account.getCurrency() : balance.currency().toUpperCase());
            entity.setObservedAt(balance.observedAt() == null ? Instant.now() : balance.observedAt());
            entity.setReferenceDate(balance.referenceDate());
            entity.setSourceHash(sourceHash);
            balances.save(entity);
            imported++;
        }
        return imported;
    }

    public int importBalancesQuietly(FinancialAccountEntity account) {
        try {
            return importBalances(account);
        } catch (EnableBankingGateway.RateLimitedException | EnableBankingGateway.ProviderException ex) {
            log.info(
                    "Balance import skipped for account {} ({})",
                    account.getId(),
                    ex.getClass().getSimpleName());
            return 0;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
