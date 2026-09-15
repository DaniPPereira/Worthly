package com.worthly.banking.application;

import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotEntity;
import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotRepository;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.identity.application.OwnerService;
import com.worthly.identity.domain.Owner;
import com.worthly.shared.web.ApiException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankingQueryService {

    private final FinancialAccountRepository accounts;
    private final BalanceSnapshotRepository balances;
    private final TransactionRepository transactions;
    private final ProviderConnectionRepository connections;
    private final OwnerService ownerService;

    public BankingQueryService(
            FinancialAccountRepository accounts,
            BalanceSnapshotRepository balances,
            TransactionRepository transactions,
            ProviderConnectionRepository connections,
            OwnerService ownerService) {
        this.accounts = accounts;
        this.balances = balances;
        this.transactions = transactions;
        this.connections = connections;
        this.ownerService = ownerService;
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
    public Page<TransactionEntity> listTransactions(UUID userId, TransactionQuery query, Pageable pageable) {
        List<UUID> accountIds = accountIds(userId, query.accountId());
        if (accountIds.isEmpty()) {
            return Page.empty(pageable);
        }
        ZoneId zone = zoneOf(userId);
        return transactions.findAll(TransactionSpecifications.filter(accountIds, query, zone), pageable);
    }

    @Transactional(readOnly = true)
    public List<TransactionEntity> listTransactionsForExport(UUID userId, TransactionQuery query) {
        List<UUID> accountIds = accountIds(userId, query.accountId());
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return transactions.findAll(
                TransactionSpecifications.filter(accountIds, query, zoneOf(userId)),
                Sort.by("reportingAt").descending());
    }

    public String providerOf(FinancialAccountEntity account) {
        return connections
                .findById(account.getConnectionId())
                .map(ProviderConnectionEntity::getProvider)
                .orElse("ENABLE_BANKING");
    }

    private List<UUID> accountIds(UUID userId, UUID accountId) {
        if (accountId != null) {
            return List.of(requireAccount(userId, accountId).getId());
        }
        return accounts.findByUserIdOrderByDisplayNameAsc(userId).stream()
                .map(FinancialAccountEntity::getId)
                .toList();
    }

    private ZoneId zoneOf(UUID userId) {
        Owner owner = ownerService.require(userId);
        return ZoneId.of(owner.reportingTimezone());
    }
}
