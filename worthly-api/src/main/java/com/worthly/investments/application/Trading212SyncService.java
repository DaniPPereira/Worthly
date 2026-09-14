package com.worthly.investments.application;

import com.worthly.audit.application.AuditService;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.transactions.adapter.out.persistence.ExternalTransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.ExternalTransactionRepository;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.categories.application.CategorizationService;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.connections.application.ConnectionService;
import com.worthly.investments.adapter.out.persistence.InvestmentAccountEntity;
import com.worthly.investments.adapter.out.persistence.InvestmentAccountRepository;
import com.worthly.investments.adapter.out.persistence.InvestmentEventEntity;
import com.worthly.investments.adapter.out.persistence.InvestmentEventRepository;
import com.worthly.investments.adapter.out.persistence.PositionSnapshotEntity;
import com.worthly.investments.adapter.out.persistence.PositionSnapshotRepository;
import com.worthly.shared.web.ApiException;
import com.worthly.sync.adapter.out.persistence.SyncRunEntity;
import com.worthly.sync.adapter.out.persistence.SyncRunRepository;
import com.worthly.sync.application.ConnectionLock;
import com.worthly.transfers.application.TransferMatchingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Trading212SyncService {

    public static final String CASH_KEY = "__cash__";
    public static final String PORTFOLIO_KEY = "__portfolio__";
    private static final int MAX_PAGES = 100;

    private final ConnectionService connectionService;
    private final Trading212ConnectionService trading212Connections;
    private final Trading212Gateway gateway;
    private final ProviderConnectionRepository connections;
    private final InvestmentAccountRepository investmentAccounts;
    private final PositionSnapshotRepository positions;
    private final InvestmentEventRepository events;
    private final FinancialAccountRepository financialAccounts;
    private final ExternalTransactionRepository externalTransactions;
    private final TransactionRepository transactions;
    private final SyncRunRepository syncRuns;
    private final CategorizationService categorization;
    private final TransferMatchingService transferMatching;
    private final AuditService auditService;
    private final ConnectionLock connectionLock;
    private final TransactionTemplate transactionTemplate;

    public Trading212SyncService(
            ConnectionService connectionService,
            Trading212ConnectionService trading212Connections,
            Trading212Gateway gateway,
            ProviderConnectionRepository connections,
            InvestmentAccountRepository investmentAccounts,
            PositionSnapshotRepository positions,
            InvestmentEventRepository events,
            FinancialAccountRepository financialAccounts,
            ExternalTransactionRepository externalTransactions,
            TransactionRepository transactions,
            SyncRunRepository syncRuns,
            CategorizationService categorization,
            TransferMatchingService transferMatching,
            AuditService auditService,
            ConnectionLock connectionLock,
            PlatformTransactionManager transactionManager) {
        this.connectionService = connectionService;
        this.trading212Connections = trading212Connections;
        this.gateway = gateway;
        this.connections = connections;
        this.investmentAccounts = investmentAccounts;
        this.positions = positions;
        this.events = events;
        this.financialAccounts = financialAccounts;
        this.externalTransactions = externalTransactions;
        this.transactions = transactions;
        this.syncRuns = syncRuns;
        this.categorization = categorization;
        this.transferMatching = transferMatching;
        this.auditService = auditService;
        this.connectionLock = connectionLock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public SyncRunEntity requestSync(UUID userId, UUID connectionId) {
        java.util.concurrent.atomic.AtomicReference<SyncRunEntity> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        boolean locked = connectionLock.tryWithLock(
                connectionId, () -> result.set(runLocked(userId, connectionId, "MANUAL", true)));
        if (!locked) {
            throw ApiException.of(HttpStatus.CONFLICT, "sync_already_running");
        }
        return result.get();
    }

    public void requestScheduledSync(ProviderConnectionEntity connection) {
        connectionLock.tryWithLock(
                connection.getId(), () -> runLocked(connection.getUserId(), connection.getId(), "SCHEDULED", false));
    }

    private SyncRunEntity runLocked(UUID userId, UUID connectionId, String triggerType, boolean failIfBusy) {
        return transactionTemplate.execute(status -> execute(userId, connectionId, triggerType, failIfBusy));
    }

    private SyncRunEntity execute(UUID userId, UUID connectionId, String triggerType, boolean failIfBusy) {
        ProviderConnectionEntity connection = connectionService.requireOwned(userId, connectionId);
        if (!Trading212ConnectionService.PROVIDER.equals(connection.getProvider())) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_provider");
        }
        if ("DISABLED".equals(connection.getStatus())) {
            if (failIfBusy) {
                throw ApiException.of(HttpStatus.BAD_REQUEST, "connection_disabled");
            }
            return null;
        }
        var stored = trading212Connections.credentialsFor(connection);
        if (stored.isEmpty() && !gateway.credentialsPresent()) {
            connection.setStatus("CONFIGURATION_REQUIRED");
            connection.setLastErrorCode("missing_credentials");
            connections.save(connection);
            if (failIfBusy) {
                throw ApiException.of(HttpStatus.BAD_REQUEST, "configuration_required");
            }
            return null;
        }
        stored.ifPresent(Trading212CredentialContext::set);
        try {
            return executeWithCredentials(userId, connectionId, connection, triggerType, failIfBusy);
        } finally {
            Trading212CredentialContext.clear();
        }
    }

    private SyncRunEntity executeWithCredentials(
            UUID userId,
            UUID connectionId,
            ProviderConnectionEntity connection,
            String triggerType,
            boolean failIfBusy) {
        if (syncRuns.existsByConnectionIdAndStatus(connectionId, "RUNNING")
                || syncRuns.existsByConnectionIdAndStatus(connectionId, "QUEUED")) {
            if (failIfBusy) {
                throw ApiException.of(HttpStatus.CONFLICT, "sync_already_running");
            }
            return null;
        }
        SyncRunEntity run = new SyncRunEntity();
        run.setConnectionId(connectionId);
        run.setTriggerType(triggerType);
        run.setStatus("RUNNING");
        run.setCorrelationId(correlationId());
        syncRuns.save(run);
        boolean snapshotSaved = false;
        try {
            Trading212Models.AccountSummary summary = gateway.accountSummary();
            InvestmentAccountEntity investmentAccount = upsertInvestmentAccount(connection, summary);
            FinancialAccountEntity brokerage = upsertBrokerageAccount(connection, summary);
            Instant observedAt = summary.observedAt();
            int imported = 0;
            imported += persistSummarySnapshots(investmentAccount, summary, observedAt);
            imported += persistPositions(investmentAccount, gateway.positions(), summary.currency(), observedAt);
            snapshotSaved = true;
            int[] history = importHistory(investmentAccount, brokerage);
            imported += history[0];
            run.setStatus("SUCCEEDED");
            run.setImportedCount(imported);
            run.setUpdatedCount(history[1]);
            run.setFinishedAt(Instant.now());
            connection.setStatus("ACTIVE");
            connection.setLastErrorCode(null);
            connection.setLastSuccessfulSyncAt(Instant.now());
            connections.save(connection);
            syncRuns.save(run);
            transferMatching.recalculate(userId);
            auditService.record(userId, "SYNC_SUCCEEDED", Map.of("connectionId", connectionId.toString(), "provider", "TRADING_212"));
            return run;
        } catch (Trading212Gateway.RateLimitedException ex) {
            run.setStatus("RATE_LIMITED");
            run.setErrorCode("provider_rate_limited");
            run.setNextRetryAt(ex.retryAt());
            run.setFinishedAt(Instant.now());
            connection.setLastErrorCode("provider_rate_limited");
            if (snapshotSaved) {
                connection.setLastSuccessfulSyncAt(Instant.now());
            }
            connections.save(connection);
            syncRuns.save(run);
            return run;
        } catch (Trading212Gateway.ProviderException ex) {
            if (ex.status() == 401 || ex.status() == 403) {
                connection.setStatus("ERROR");
            }
            run.setStatus("FAILED");
            run.setErrorCode(ex.code());
            run.setFinishedAt(Instant.now());
            connection.setLastErrorCode(ex.code());
            connections.save(connection);
            syncRuns.save(run);
            return run;
        }
    }

    private InvestmentAccountEntity upsertInvestmentAccount(
            ProviderConnectionEntity connection, Trading212Models.AccountSummary summary) {
        InvestmentAccountEntity entity = investmentAccounts
                .findByProviderConnectionIdAndProviderAccountId(connection.getId(), summary.accountId())
                .orElseGet(InvestmentAccountEntity::new);
        entity.setUserId(connection.getUserId());
        entity.setProviderConnectionId(connection.getId());
        entity.setProviderAccountId(summary.accountId());
        entity.setCurrency(summary.currency());
        return investmentAccounts.save(entity);
    }

    private FinancialAccountEntity upsertBrokerageAccount(
            ProviderConnectionEntity connection, Trading212Models.AccountSummary summary) {
        FinancialAccountEntity entity = financialAccounts
                .findByConnectionIdAndProviderAccountAliasAndActiveIsTrue(connection.getId(), summary.accountId())
                .orElseGet(FinancialAccountEntity::new);
        entity.setUserId(connection.getUserId());
        entity.setConnectionId(connection.getId());
        entity.setProviderAccountAlias(summary.accountId());
        entity.setType("BROKERAGE");
        entity.setDisplayName("Trading 212");
        entity.setCurrency(summary.currency());
        entity.setActive(true);
        return financialAccounts.save(entity);
    }

    private int persistSummarySnapshots(
            InvestmentAccountEntity account, Trading212Models.AccountSummary summary, Instant observedAt) {
        saveSnapshot(account.getId(), CASH_KEY, "CASH", BigDecimal.ONE, summary.cash(), summary.currency(), observedAt);
        saveSnapshot(
                account.getId(),
                PORTFOLIO_KEY,
                "PORTFOLIO",
                BigDecimal.ONE,
                summary.portfolioValue(),
                summary.currency(),
                observedAt);
        return 2;
    }

    private int persistPositions(
            InvestmentAccountEntity account,
            List<Trading212Models.Position> providerPositions,
            String fallbackCurrency,
            Instant observedAt) {
        int imported = 0;
        for (Trading212Models.Position position : providerPositions) {
            saveSnapshot(
                    account.getId(),
                    position.instrumentKey(),
                    position.ticker(),
                    position.quantity(),
                    position.marketValue(),
                    position.currency() == null ? fallbackCurrency : position.currency(),
                    observedAt);
            imported++;
        }
        return imported;
    }

    private void saveSnapshot(
            UUID accountId,
            String instrumentKey,
            String ticker,
            BigDecimal quantity,
            BigDecimal marketValue,
            String currency,
            Instant observedAt) {
        PositionSnapshotEntity snapshot = new PositionSnapshotEntity();
        snapshot.setInvestmentAccountId(accountId);
        snapshot.setInstrumentKey(instrumentKey);
        snapshot.setTicker(ticker);
        snapshot.setQuantity(quantity == null ? BigDecimal.ZERO : quantity);
        snapshot.setMarketValue(marketValue);
        snapshot.setCurrency(currency);
        snapshot.setObservedAt(observedAt);
        positions.save(snapshot);
    }

    private int[] importHistory(InvestmentAccountEntity investmentAccount, FinancialAccountEntity brokerage) {
        int imported = 0;
        int updated = 0;
        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Trading212Models.HistoryPage result = gateway.transactions(cursor);
            for (Trading212Models.HistoryEvent event : result.items()) {
                int outcome = persistEvent(investmentAccount, brokerage, event);
                if (outcome == 1) {
                    imported++;
                } else if (outcome == 2) {
                    updated++;
                }
            }
            cursor = result.nextPagePath();
            if (cursor == null || cursor.isBlank()) {
                break;
            }
        }
        cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Trading212Models.HistoryPage result = gateway.dividends(cursor);
            for (Trading212Models.HistoryEvent event : result.items()) {
                int outcome = persistEvent(investmentAccount, brokerage, event);
                if (outcome == 1) {
                    imported++;
                } else if (outcome == 2) {
                    updated++;
                }
            }
            cursor = result.nextPagePath();
            if (cursor == null || cursor.isBlank()) {
                break;
            }
        }
        return new int[] {imported, updated};
    }

    private int persistEvent(
            InvestmentAccountEntity investmentAccount, FinancialAccountEntity brokerage, Trading212Models.HistoryEvent event) {
        InvestmentEventEntity existing = events
                .findByInvestmentAccountIdAndProviderEventId(investmentAccount.getId(), event.providerEventId())
                .orElse(null);
        if (existing != null) {
            existing.setAmount(event.amount());
            existing.setCurrency(event.currency());
            existing.setOccurredAt(event.occurredAt());
            events.save(existing);
            return 2;
        }
        InvestmentEventEntity entity = new InvestmentEventEntity();
        entity.setInvestmentAccountId(investmentAccount.getId());
        entity.setProviderEventId(event.providerEventId());
        entity.setEventType(event.eventType());
        entity.setAmount(event.amount() == null ? null : event.amount().abs());
        entity.setCurrency(event.currency());
        entity.setOccurredAt(event.occurredAt());
        entity.setInstrumentKey(event.instrumentKey());
        entity.setQuantity(event.quantity());
        events.save(entity);
        persistNormalizedMovement(brokerage, event);
        return 1;
    }

    private void persistNormalizedMovement(FinancialAccountEntity brokerage, Trading212Models.HistoryEvent event) {
        String type = event.eventType() == null ? "" : event.eventType().toUpperCase();
        if (!List.of("DEPOSIT", "WITHDRAW", "FEE", "INTEREST", "INTEREST_ON_FREE_CASH", "LENDING_INTEREST", "DIVIDEND")
                .contains(type)) {
            return;
        }
        ExternalTransactionEntity source = new ExternalTransactionEntity();
        source.setAccountId(brokerage.getId());
        source.setProviderTransactionId("t212:" + event.providerEventId());
        source.setProviderStatus("BOOK");
        source.setAmount(event.amount() == null ? BigDecimal.ZERO : event.amount().abs());
        source.setCurrency(event.currency() == null ? brokerage.getCurrency() : event.currency().toUpperCase());
        source.setBookedAt(event.occurredAt());
        source.setDescription(type);
        source.setCounterparty("Trading 212");
        externalTransactions.save(source);

        TransactionEntity tx = new TransactionEntity();
        tx.setAccountId(brokerage.getId());
        tx.setExternalTransactionId(source.getId());
        tx.setDirection(directionFor(type));
        tx.setLifecycleStatus("BOOKED");
        tx.setEconomicType("OTHER");
        tx.setAmount(source.getAmount());
        tx.setCurrency(source.getCurrency());
        tx.setMerchant("Trading 212");
        tx.setDescription(type);
        tx.setReportingAt(event.occurredAt());
        tx.setCategorizationSource("UNCATEGORIZED");
        if ("DEPOSIT".equals(type) || "WITHDRAW".equals(type)) {
            tx.setEconomicType("DEPOSIT".equals(type) ? "INVESTMENT_FUNDING" : "INVESTMENT_WITHDRAWAL");
        } else if ("FEE".equals(type)) {
            tx.setEconomicType("FEE");
        } else {
            tx.setEconomicType("INCOME");
        }
        categorization.applyAutomatic(brokerage.getUserId(), tx);
        if ("DEPOSIT".equals(type) || "WITHDRAW".equals(type) || "FEE".equals(type) || "DIVIDEND".equals(type)
                || type.contains("INTEREST")) {
            // keep mapped economic type unless a manual/rule assignment already applied
            if (!"MANUAL".equals(tx.getCategorizationSource()) && !"RULE".equals(tx.getCategorizationSource())) {
                if ("DEPOSIT".equals(type)) {
                    tx.setEconomicType("INVESTMENT_FUNDING");
                } else if ("WITHDRAW".equals(type)) {
                    tx.setEconomicType("INVESTMENT_WITHDRAWAL");
                } else if ("FEE".equals(type)) {
                    tx.setEconomicType("FEE");
                } else {
                    tx.setEconomicType("INCOME");
                }
            }
        }
        transactions.save(tx);
    }

    private static String directionFor(String type) {
        return switch (type) {
            case "WITHDRAW", "FEE" -> "DEBIT";
            default -> "CREDIT";
        };
    }

    private static String correlationId() {
        String existing = MDC.get("correlationId");
        return existing == null ? UUID.randomUUID().toString() : existing;
    }
}
