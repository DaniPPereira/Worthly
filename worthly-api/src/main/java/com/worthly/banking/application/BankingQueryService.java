package com.worthly.banking.application;

import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotEntity;
import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotRepository;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.shared.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankingQueryService {

    private final FinancialAccountRepository accounts;
    private final BalanceSnapshotRepository balances;
    private final TransactionRepository transactions;
    private final ProviderConnectionRepository connections;

    public BankingQueryService(
            FinancialAccountRepository accounts,
            BalanceSnapshotRepository balances,
            TransactionRepository transactions,
            ProviderConnectionRepository connections) {
        this.accounts = accounts;
        this.balances = balances;
        this.transactions = transactions;
        this.connections = connections;
    }

    @Transactional(readOnly = true)
    public List<FinancialAccountEntity> listAccounts(UUID userId) {
        return accounts.findByUserIdOrderByDisplayNameAsc(userId);
    }

    @Transactional(readOnly = true)
    public FinancialAccountEntity requireAccount(UUID userId, UUID accountId) {
        return accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
    }

    @Transactional(readOnly = true)
    public List<BalanceSnapshotEntity> listBalances(UUID userId, UUID accountId) {
        FinancialAccountEntity account = requireAccount(userId, accountId);
        return balances.findByAccountIdOrderByObservedAtDesc(account.getId());
    }

    @Transactional(readOnly = true)
    public Page<TransactionEntity> listTransactions(UUID userId, UUID accountId, Pageable pageable) {
        if (accountId != null) {
            FinancialAccountEntity account = accounts
                    .findByIdAndUserId(accountId, userId)
                    .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
            return transactions.findByAccountId(account.getId(), pageable);
        }
        List<UUID> ids = accounts.findByUserIdOrderByDisplayNameAsc(userId).stream()
                .map(FinancialAccountEntity::getId)
                .toList();
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        return transactions.findByAccountIdIn(ids, pageable);
    }

    public String providerOf(FinancialAccountEntity account) {
        return connections
                .findById(account.getConnectionId())
                .map(ProviderConnectionEntity::getProvider)
                .orElse("ENABLE_BANKING");
    }
}
