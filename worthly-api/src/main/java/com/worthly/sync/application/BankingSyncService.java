package com.worthly.sync.application;

import com.worthly.audit.application.AuditService;
import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotEntity;
import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotRepository;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
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
import com.worthly.shared.web.ApiException;
import com.worthly.sync.adapter.out.persistence.SyncRunEntity;
import com.worthly.sync.adapter.out.persistence.SyncRunRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankingSyncService {

    private static final Duration RAW_RETENTION = Duration.ofDays(30);
    private static final int MAX_PAGES = 100;

    private final ConnectionService connectionService;
    private final EnableBankingGateway gateway;
    private final ProviderConnectionRepository connections;
    private final FinancialAccountRepository accounts;
    private final BalanceSnapshotRepository balances;
    private final ExternalTransactionRepository externalTransactions;
    private final TransactionRepository transactions;
    private final SyncRunRepository syncRuns;
    private final PayloadCrypto payloadCrypto;
    private final AuditService auditService;
    private final WorthlyProperties.EnableBanking properties;

    public BankingSyncService(
            ConnectionService connectionService,
            EnableBankingGateway gateway,
            ProviderConnectionRepository connections,
            FinancialAccountRepository accounts,
            BalanceSnapshotRepository balances,
            ExternalTransactionRepository externalTransactions,
            TransactionRepository transactions,
            SyncRunRepository syncRuns,
            PayloadCrypto payloadCrypto,
            AuditService auditService,
            WorthlyProperties properties) {
        this.connectionService = connectionService;
        this.gateway = gateway;
        this.connections = connections;
        this.accounts = accounts;
        this.balances = balances;
        this.externalTransactions = externalTransactions;
        this.transactions = transactions;
        this.syncRuns = syncRuns;
        this.payloadCrypto = payloadCrypto;
        this.auditService = auditService;
        this.properties = properties.getEnableBanking();
    }

    @Transactional
    public SyncRunEntity requestSync(UUID userId, UUID connectionId) {
        ProviderConnectionEntity connection = connectionService.requireOwned(userId, connectionId);
        if ("DISABLED".equals(connection.getStatus())) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "connection_disabled");
        }
        if (syncRuns.existsByConnectionIdAndStatus(connectionId, "RUNNING")) {
            throw ApiException.of(HttpStatus.CONFLICT, "sync_already_running");
        }
        SyncRunEntity run = new SyncRunEntity();
        run.setConnectionId(connectionId);
        run.setTriggerType("MANUAL");
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
            for (EnableBankingModels.ProviderAccount account : session.accounts()) {
                connectionService.upsertAccount(connection, account);
            }
            int imported = 0;
            int updated = 0;
            LocalDate dateTo = LocalDate.now(ZoneOffset.UTC);
            LocalDate dateFrom = dateTo.minusDays(properties.getTransactionLookback().toDays());
            for (FinancialAccountEntity account : accounts.findByConnectionId(connection.getId())) {
                imported += importBalances(account);
                int[] counts = importTransactions(account, dateFrom, dateTo);
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
        }
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SyncRunEntity> listRuns(
            UUID userId, UUID connectionId, org.springframework.data.domain.Pageable pageable) {
        connectionService.requireOwned(userId, connectionId);
        return syncRuns.findByConnectionIdOrderByStartedAtDesc(connectionId, pageable);
    }

    private int importBalances(FinancialAccountEntity account) {
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

    private int[] importTransactions(FinancialAccountEntity account, LocalDate dateFrom, LocalDate dateTo) {
        int imported = 0;
        int updated = 0;
        String continuation = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            EnableBankingModels.TransactionPage result =
                    gateway.listTransactions(account.getProviderAccountAlias(), dateFrom, dateTo, continuation);
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
                        normalized.setReportingAt(bookedAt);
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
        normalized.setReportingAt(bookedAt);
        normalized.setCategorizationSource("UNCATEGORIZED");
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
        return run;
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
