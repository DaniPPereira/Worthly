package com.worthly.sync.application;

import com.worthly.audit.application.AuditService;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.application.BalanceSnapshotImporter;
import com.worthly.banking.application.BankingMappings;
import com.worthly.banking.transactions.adapter.out.persistence.ExternalTransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.ExternalTransactionRepository;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.connections.application.ConnectionService;
import com.worthly.connections.application.EnableBankingGateway;
import com.worthly.connections.application.EnableBankingModels;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.crypto.PayloadCrypto;
import com.worthly.infrastructure.security.TokenHashes;
import com.worthly.notifications.application.NotificationService;
import com.worthly.shared.web.ApiException;
import com.worthly.categories.application.CategorizationService;
import com.worthly.sync.adapter.out.persistence.SyncRunEntity;
import com.worthly.sync.adapter.out.persistence.SyncRunRepository;
import com.worthly.transfers.application.TransferMatchingService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BankingSyncService implements ConnectionSyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(BankingSyncService.class);
    private static final Duration RAW_RETENTION = Duration.ofDays(30);
    private static final int MAX_PAGES = 250;

    private final ConnectionService connectionService;
    private final EnableBankingGateway gateway;
    private final ProviderConnectionRepository connections;
    private final FinancialAccountRepository accounts;
    private final BalanceSnapshotImporter balanceImporter;
    private final ExternalTransactionRepository externalTransactions;
    private final TransactionRepository transactions;
    private final SyncRunRepository syncRuns;
    private final PayloadCrypto payloadCrypto;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ConnectionLock connectionLock;
    private final TransactionTemplate transactionTemplate;
    private final CategorizationService categorization;
    private final TransferMatchingService transferMatching;
    private final WorthlyProperties.EnableBanking properties;

    public BankingSyncService(
            ConnectionService connectionService,
            EnableBankingGateway gateway,
            ProviderConnectionRepository connections,
            FinancialAccountRepository accounts,
            BalanceSnapshotImporter balanceImporter,
            ExternalTransactionRepository externalTransactions,
            TransactionRepository transactions,
            SyncRunRepository syncRuns,
            PayloadCrypto payloadCrypto,
            AuditService auditService,
            NotificationService notificationService,
            ConnectionLock connectionLock,
            PlatformTransactionManager transactionManager,
            CategorizationService categorization,
            TransferMatchingService transferMatching,
            WorthlyProperties properties) {
        this.connectionService = connectionService;
        this.gateway = gateway;
        this.connections = connections;
        this.accounts = accounts;
        this.balanceImporter = balanceImporter;
        this.externalTransactions = externalTransactions;
        this.transactions = transactions;
        this.syncRuns = syncRuns;
        this.payloadCrypto = payloadCrypto;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.connectionLock = connectionLock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.categorization = categorization;
        this.transferMatching = transferMatching;
        this.properties = properties.getEnableBanking();
    }

    @Override
    public boolean supports(ProviderConnectionEntity connection) {
        return ConnectionService.PROVIDER.equals(connection.getProvider());
    }

    @Override
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

    @Override
    public void requestScheduledSync(ProviderConnectionEntity connection) {
        connectionLock.tryWithLock(
                connection.getId(), () -> runLocked(connection.getUserId(), connection.getId(), "SCHEDULED", false));
    }

    private SyncRunEntity runLocked(UUID userId, UUID connectionId, String triggerType, boolean failIfBusy) {
        return transactionTemplate.execute(
                status -> executeSync(userId, connectionId, triggerType, failIfBusy));
    }

    private SyncRunEntity executeSync(UUID userId, UUID connectionId, String triggerType, boolean failIfBusy) {
        ProviderConnectionEntity connection = connectionService.requireOwned(userId, connectionId);
        if ("DISABLED".equals(connection.getStatus())) {
            if (failIfBusy) {
                throw ApiException.of(HttpStatus.BAD_REQUEST, "connection_disabled");
            }
            return null;
        }
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

        if (connection.getExternalSessionIdEncrypted() == null) {
            return fail(run, connection, "configuration_required", "CONFIGURATION_REQUIRED");
        }
        try {
            String sessionId = payloadCrypto.decryptUtf8(connection.getExternalSessionIdEncrypted());
            EnableBankingModels.ProviderSession session = gateway.getSession(sessionId);
            String mapped = BankingMappings.connectionStatus(session.status());
            connection.setStatus(mapped);
            connection.setConsentExpiresAt(session.expiresAt());
            if ("REAUTH_REQUIRED".equals(mapped) || "DISABLED".equals(mapped) || "ERROR".equals(mapped)) {
                return fail(run, connection, "reauth_required", mapped);
            }
            List<EnableBankingModels.ProviderAccount> sessionAccounts =
                    session.accounts() == null ? List.of() : session.accounts();
            for (EnableBankingModels.ProviderAccount account : sessionAccounts) {
                connectionService.upsertAccount(connection, account);
            }
            int imported = 0;
            int updated = 0;
            boolean fullHistory = "MANUAL".equals(triggerType) || connection.getLastSuccessfulSyncAt() == null;
            LocalDate dateTo = fullHistory ? null : LocalDate.now(ZoneOffset.UTC);
            LocalDate dateFrom = fullHistory ? null : dateTo.minusDays(properties.getTransactionLookback().toDays());
            String strategy = fullHistory ? "longest" : "default";
            for (FinancialAccountEntity account : accounts.findByConnectionId(connection.getId())) {
                imported += balanceImporter.importBalances(account);
                int[] counts = importTransactions(account, dateFrom, dateTo, strategy);
                imported += counts[0];
                updated += counts[1];
            }
            run.setStatus("SUCCEEDED");
            run.setImportedCount(imported);
            run.setUpdatedCount(updated);
            run.setFinishedAt(Instant.now());
            connection.setLastSuccessfulSyncAt(Instant.now());
            connection.setLastErrorCode(null);
            connections.save(connection);
            syncRuns.save(run);
            notificationService.reconcileConnectionAlerts(userId);
            transferMatching.recalculate(userId);
            auditService.record(
                    userId,
                    "SYNC_SUCCEEDED",
                    Map.of("connectionId", connectionId.toString(), "imported", imported, "updated", updated));
            return run;
        } catch (EnableBankingGateway.RateLimitedException ex) {
            run.setStatus("RATE_LIMITED");
            run.setErrorCode("aspsp_rate_limited");
            run.setNextRetryAt(ex.retryAt());
            run.setFinishedAt(Instant.now());
            connection.setLastErrorCode("aspsp_rate_limited");
            connections.save(connection);
            syncRuns.save(run);
            return run;
        } catch (EnableBankingGateway.ProviderException ex) {
            String mapped = mapProviderFailure(ex);
            return fail(run, connection, ex.code(), mapped);
        } catch (RuntimeException ex) {
            log.info(
                    "Sync failed for connection {} ({})",
                    connectionId,
                    ex.getClass().getSimpleName());
            String code = sessionUnreadable(ex) ? "session_unreadable" : "sync_failed";
            return fail(run, connection, code, "ERROR");
        }
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SyncRunEntity> listRuns(
            UUID userId, UUID connectionId, org.springframework.data.domain.Pageable pageable) {
        connectionService.requireOwned(userId, connectionId);
        return syncRuns.findByConnectionIdOrderByStartedAtDesc(connectionId, pageable);
    }

    private int[] importTransactions(
            FinancialAccountEntity account, LocalDate dateFrom, LocalDate dateTo, String strategy) {
        int imported = 0;
        int updated = 0;
        String continuation = null;
        String activeStrategy = strategy;
        LocalDate activeFrom = dateFrom;
        LocalDate activeTo = dateTo;
        for (int page = 0; page < MAX_PAGES; page++) {
            EnableBankingModels.TransactionPage result;
            try {
                result = gateway.listTransactions(
                        account.getProviderAccountAlias(), activeFrom, activeTo, continuation, activeStrategy);
            } catch (EnableBankingGateway.ProviderException ex) {
                if (page == 0 && "WRONG_TRANSACTIONS_PERIOD".equalsIgnoreCase(ex.code())) {
                    LocalDate fallbackTo = LocalDate.now(ZoneOffset.UTC);
                    activeStrategy = "default";
                    activeFrom = fallbackTo.minusDays(89);
                    activeTo = fallbackTo;
                    continuation = null;
                    result = gateway.listTransactions(
                            account.getProviderAccountAlias(), activeFrom, activeTo, null, activeStrategy);
                } else {
                    throw ex;
                }
            }
            for (EnableBankingModels.ProviderTransaction tx : result.transactions()) {
                int outcome = persistTransaction(account, tx);
                if (outcome == 1) {
                    imported++;
                } else if (outcome == 2) {
                    updated++;
                }
            }
            continuation = result.continuationKey();
            if (continuation == null || continuation.isBlank()) {
                break;
            }
        }
        return new int[] {imported, updated};
    }

    private int persistTransaction(FinancialAccountEntity account, EnableBankingModels.ProviderTransaction tx) {
        ExternalTransactionEntity existing = null;
        String fingerprint = fallbackFingerprint(account, tx);
        if (tx.transactionId() != null && !tx.transactionId().isBlank()) {
            existing = externalTransactions
                    .findByAccountIdAndProviderTransactionId(account.getId(), tx.transactionId())
                    .orElse(null);
        } else if (fingerprint != null) {
            existing = externalTransactions
                    .findByAccountIdAndFallbackFingerprint(account.getId(), fingerprint)
                    .orElse(null);
        }
        BigDecimal amount = tx.amount() == null ? BigDecimal.ZERO : tx.amount().abs();
        String currency = tx.currency() == null ? account.getCurrency() : tx.currency().toUpperCase();
        Instant bookedAt = tx.bookingDate() == null
                ? Instant.now()
                : tx.bookingDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        if (existing != null) {
            existing.setProviderStatus(tx.status() == null ? "UNKNOWN" : tx.status());
            existing.setAmount(amount);
            existing.setCurrency(currency);
            existing.setBookedAt(bookedAt);
            existing.setValueDate(tx.valueDate());
            existing.setDescription(tx.description());
            existing.setCounterparty(tx.counterparty());
            externalTransactions.save(existing);
            transactions
                    .findByExternalTransactionId(existing.getId())
                    .ifPresent(normalized -> {
                        normalized.setLifecycleStatus(BankingMappings.lifecycle(tx.status()));
                        normalized.setAmount(amount);
                        normalized.setCurrency(currency);
                        normalized.setDescription(tx.description());
                        normalized.setMerchant(tx.counterparty());
                        normalized.setLocation(tx.location());
                        normalized.setReportingAt(bookedAt);
                        categorization.applyAutomatic(account.getUserId(), normalized);
                        transactions.save(normalized);
                    });
            return 2;
        }
        ExternalTransactionEntity source = new ExternalTransactionEntity();
        source.setAccountId(account.getId());
        source.setProviderTransactionId(tx.transactionId());
        source.setFallbackFingerprint(tx.transactionId() == null ? fingerprint : null);
        source.setProviderStatus(tx.status() == null ? "UNKNOWN" : tx.status());
        source.setAmount(amount);
        source.setCurrency(currency);
        source.setBookedAt(bookedAt);
        source.setValueDate(tx.valueDate());
        source.setDescription(tx.description());
        source.setCounterparty(tx.counterparty());
        if (tx.rawJson() != null) {
            source.setRawPayloadEncrypted(payloadCrypto.encryptUtf8(tx.rawJson()));
            source.setRawKeyVersion(1);
            source.setRawExpiresAt(Instant.now().plus(RAW_RETENTION));
        }
        externalTransactions.save(source);

        TransactionEntity normalized = new TransactionEntity();
        normalized.setAccountId(account.getId());
        normalized.setExternalTransactionId(source.getId());
        normalized.setDirection(BankingMappings.direction(tx.creditDebitIndicator()));
        normalized.setLifecycleStatus(BankingMappings.lifecycle(tx.status()));
        normalized.setEconomicType("OTHER");
        normalized.setAmount(amount);
        normalized.setCurrency(currency);
        normalized.setMerchant(tx.counterparty());
        normalized.setDescription(tx.description());
        normalized.setLocation(tx.location());
        normalized.setReportingAt(bookedAt);
        normalized.setCategorizationSource("UNCATEGORIZED");
        categorization.applyAutomatic(account.getUserId(), normalized);
        transactions.save(normalized);
        return 1;
    }

    private SyncRunEntity fail(
            SyncRunEntity run, ProviderConnectionEntity connection, String errorCode, String connectionStatus) {
        run.setStatus("FAILED");
        run.setErrorCode(errorCode);
        run.setFinishedAt(Instant.now());
        connection.setStatus(connectionStatus);
        connection.setLastErrorCode(errorCode);
        connections.save(connection);
        syncRuns.save(run);
        if ("REAUTH_REQUIRED".equals(connectionStatus)) {
            notificationService.remind(connection.getUserId(), "CONNECTION_REAUTH_REQUIRED");
        }
        if ("CONFIGURATION_REQUIRED".equals(connectionStatus)) {
            notificationService.remind(connection.getUserId(), "CONFIGURATION_REQUIRED");
        }
        return run;
    }

    private static boolean sessionUnreadable(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return ex instanceof IllegalStateException && message.contains("decrypt");
    }

    private static String mapProviderFailure(EnableBankingGateway.ProviderException ex) {
        String code = ex.code() == null ? "" : ex.code().toUpperCase();
        if (ex.status() == 401
                || ex.status() == 403
                || code.contains("EXPIRED")
                || code.contains("REVOKED")
                || code.contains("INVALID")) {
            return "REAUTH_REQUIRED";
        }
        return "ERROR";
    }

    private static String fallbackFingerprint(
            FinancialAccountEntity account, EnableBankingModels.ProviderTransaction tx) {
        return TokenHashes.sha256(String.join(
                "|",
                account.getId().toString(),
                tx.amount() == null ? "0" : tx.amount().toPlainString(),
                nullToEmpty(tx.currency()),
                String.valueOf(tx.bookingDate()),
                String.valueOf(tx.valueDate()),
                nullToEmpty(tx.description()),
                nullToEmpty(tx.counterparty()),
                nullToEmpty(tx.creditDebitIndicator()),
                nullToEmpty(tx.entryReference())));
    }

    private static String correlationId() {
        String existing = MDC.get("correlationId");
        return existing == null ? UUID.randomUUID().toString() : existing;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
