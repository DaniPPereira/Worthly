package com.worthly.connections.application;

import com.worthly.audit.application.AuditService;
import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotRepository;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.application.BalanceSnapshotImporter;
import com.worthly.banking.application.BankingMappings;
import com.worthly.banking.transactions.adapter.out.persistence.ExternalTransactionRepository;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.connections.adapter.out.persistence.AuthorizationAttemptEntity;
import com.worthly.connections.adapter.out.persistence.AuthorizationAttemptRepository;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.crypto.PayloadCrypto;
import com.worthly.infrastructure.security.TokenHashes;
import com.worthly.investments.adapter.out.persistence.InvestmentAccountEntity;
import com.worthly.investments.adapter.out.persistence.InvestmentAccountRepository;
import com.worthly.investments.adapter.out.persistence.InvestmentEventRepository;
import com.worthly.investments.adapter.out.persistence.PositionSnapshotRepository;
import com.worthly.shared.web.ApiException;
import com.worthly.transfers.adapter.out.persistence.TransferMatchRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ConnectionService {

    public static final String PROVIDER = "ENABLE_BANKING";
    private final EnableBankingGateway gateway;
    private final EnableBankingDiscoveryService discoveryService;
    private final AuthorizationAttemptRepository attempts;
    private final ProviderConnectionRepository connections;
    private final FinancialAccountRepository accounts;
    private final BalanceSnapshotRepository balances;
    private final ExternalTransactionRepository externalTransactions;
    private final TransactionRepository transactions;
    private final TransferMatchRepository transferMatches;
    private final InvestmentAccountRepository investmentAccounts;
    private final PositionSnapshotRepository positionSnapshots;
    private final InvestmentEventRepository investmentEvents;
    private final PayloadCrypto payloadCrypto;
    private final AuditService auditService;
    private final BalanceSnapshotImporter balanceImporter;
    private final ApplicationEventPublisher events;
    private final WorthlyProperties.EnableBanking properties;
    private final SecureRandom random = new SecureRandom();

    public ConnectionService(
            EnableBankingGateway gateway,
            EnableBankingDiscoveryService discoveryService,
            AuthorizationAttemptRepository attempts,
            ProviderConnectionRepository connections,
            FinancialAccountRepository accounts,
            BalanceSnapshotRepository balances,
            ExternalTransactionRepository externalTransactions,
            TransactionRepository transactions,
            TransferMatchRepository transferMatches,
            InvestmentAccountRepository investmentAccounts,
            PositionSnapshotRepository positionSnapshots,
            InvestmentEventRepository investmentEvents,
            PayloadCrypto payloadCrypto,
            AuditService auditService,
            BalanceSnapshotImporter balanceImporter,
            ApplicationEventPublisher events,
            WorthlyProperties properties) {
        this.gateway = gateway;
        this.discoveryService = discoveryService;
        this.attempts = attempts;
        this.connections = connections;
        this.accounts = accounts;
        this.balances = balances;
        this.externalTransactions = externalTransactions;
        this.transactions = transactions;
        this.transferMatches = transferMatches;
        this.investmentAccounts = investmentAccounts;
        this.positionSnapshots = positionSnapshots;
        this.investmentEvents = investmentEvents;
        this.payloadCrypto = payloadCrypto;
        this.auditService = auditService;
        this.balanceImporter = balanceImporter;
        this.events = events;
        this.properties = properties.getEnableBanking();
    }

    @Transactional(readOnly = true)
    public List<ProviderConnectionEntity> list(UUID userId) {
        return connections.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(this::visibleInList)
                .toList();
    }

    /** Disconnect keeps a Disabled card so the owner can purge. After purge, hide the empty shell. */
    private boolean visibleInList(ProviderConnectionEntity connection) {
        if (!"DISABLED".equals(connection.getStatus())) {
            return true;
        }
        return accounts.existsByConnectionId(connection.getId())
                || investmentAccounts.existsByProviderConnectionId(connection.getId());
    }

    public List<EnableBankingModels.DiscoveredBank> listBanks(String country) {
        return discoveryService.listV1Banks(country);
    }

    @Transactional
    public EnableBankingModels.AuthStart authorize(UUID userId, String name, String country, String returnClient) {
        if (!gateway.configured()) {
            throw ApiException.of(HttpStatus.SERVICE_UNAVAILABLE, "configuration_required");
        }
        if (!"WEB".equals(returnClient) && !"MOBILE".equals(returnClient)) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        EnableBankingModels.DiscoveredBank bank = discoveryService.requireV1Bank(name, country);
        gateway.application().ifPresent(app -> {
            if (!EnableBankingAuthSupport.redirectRegistered(app.redirectUrls(), properties.getCallbackUrl())) {
                throw ApiException.of(HttpStatus.BAD_REQUEST, "redirect_url_mismatch");
            }
        });
        String state = randomState();
        Instant expiresAt = Instant.now().plus(properties.getAuthorizationTtl());
        Instant validUntil = EnableBankingAuthSupport.consentValidUntil(bank.maximumConsentValiditySeconds(), Instant.now());
        EnableBankingModels.AuthStart start;
        try {
            start = gateway.startAuthorization(bank.name(), bank.country(), state, validUntil);
        } catch (EnableBankingGateway.RateLimitedException ex) {
            throw ApiException.of(HttpStatus.TOO_MANY_REQUESTS, "aspsp_rate_limited");
        } catch (EnableBankingGateway.ProviderException ex) {
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "provider_error");
        }
        AuthorizationAttemptEntity attempt = new AuthorizationAttemptEntity();
        attempt.setUserId(userId);
        attempt.setProvider(PROVIDER);
        attempt.setStateHash(TokenHashes.sha256(state));
        attempt.setRedirectTarget(returnClient);
        attempt.setExpiresAt(expiresAt);
        attempt.setAspspName(bank.name());
        attempt.setAspspCountry(bank.country());
        attempts.save(attempt);
        auditService.record(userId, "ENABLE_BANKING_AUTH_STARTED", Map.of("aspspName", bank.name()));
        return new EnableBankingModels.AuthStart(start.url(), expiresAt);
    }

    @Transactional
    public String handleCallback(String code, String state, String error, String errorDescription) {
        if (state == null || state.isBlank()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_state");
        }
        AuthorizationAttemptEntity attempt = attempts
                .findByStateHash(TokenHashes.sha256(state))
                .orElseThrow(() -> ApiException.of(HttpStatus.BAD_REQUEST, "invalid_state"));
        if (attempt.getUsedAt() != null) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "state_replay");
        }
        if (attempt.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "state_expired");
        }
        attempt.setUsedAt(Instant.now());
        attempts.save(attempt);
        String resultBase = "WEB".equals(attempt.getRedirectTarget())
                ? properties.getWebResultUrl()
                : properties.getMobileResultUrl();
        if (error != null && !error.isBlank()) {
            auditService.record(
                    attempt.getUserId(),
                    "ENABLE_BANKING_AUTH_FAILED",
                    Map.of("error", error, "aspspName", String.valueOf(attempt.getAspspName())));
            return UriComponentsBuilder.fromUriString(resultBase)
                    .queryParam("status", "error")
                    .queryParam("code", error)
                    .build(true)
                    .toUriString();
        }
        if (code == null || code.isBlank()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        EnableBankingModels.ProviderSession session;
        try {
            session = gateway.createSession(code);
        } catch (EnableBankingGateway.RateLimitedException ex) {
            throw ApiException.of(HttpStatus.TOO_MANY_REQUESTS, "aspsp_rate_limited");
        } catch (EnableBankingGateway.ProviderException ex) {
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "provider_error");
        }
        ProviderConnectionEntity connection = connections
                .findFirstByUserIdAndProviderAndAspspNameAndAspspCountry(
                        attempt.getUserId(), PROVIDER, attempt.getAspspName(), attempt.getAspspCountry())
                .orElseGet(ProviderConnectionEntity::new);
        connection.setUserId(attempt.getUserId());
        connection.setProvider(PROVIDER);
        connection.setStatus(BankingMappings.connectionStatus(session.status()));
        connection.setAspspName(attempt.getAspspName());
        connection.setAspspCountry(attempt.getAspspCountry());
        connection.setExternalSessionIdEncrypted(payloadCrypto.encryptUtf8(session.sessionId()));
        connection.setConsentExpiresAt(session.expiresAt());
        connection.setLastErrorCode(null);
        connections.save(connection);
        List<EnableBankingModels.ProviderAccount> sessionAccounts =
                session.accounts() == null ? List.of() : session.accounts();
        for (EnableBankingModels.ProviderAccount account : sessionAccounts) {
            FinancialAccountEntity stored = upsertAccount(connection, account);
            if (stored != null) {
                balanceImporter.importBalancesQuietly(stored);
            }
        }
        auditService.record(
                attempt.getUserId(),
                "ENABLE_BANKING_CONNECTED",
                Map.of("connectionId", connection.getId().toString(), "aspspName", attempt.getAspspName()));
        if ("ACTIVE".equals(connection.getStatus())) {
            events.publishEvent(new ConnectionEstablishedEvent(attempt.getUserId(), connection.getId()));
        }
        return UriComponentsBuilder.fromUriString(resultBase)
                .queryParam("status", "ok")
                .queryParam("connectionId", connection.getId().toString())
                .build(true)
                .toUriString();
    }

    @Transactional
    public void disconnect(UUID userId, UUID connectionId) {
        ProviderConnectionEntity connection = requireOwned(userId, connectionId);
        revokeSessionQuietly(connection);
        connection.setStatus("DISABLED");
        connection.setExternalSessionIdEncrypted(null);
        connections.save(connection);
        auditService.record(userId, "CONNECTION_DISCONNECTED", Map.of("connectionId", connectionId.toString()));
    }

    @Transactional
    public void purge(UUID userId, UUID connectionId, boolean confirm) {
        if (!confirm) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "confirm_required");
        }
        ProviderConnectionEntity connection = requireOwned(userId, connectionId);
        List<InvestmentAccountEntity> investmentOwned = investmentAccounts.findByProviderConnectionId(connection.getId());
        List<UUID> investmentIds = investmentOwned.stream().map(InvestmentAccountEntity::getId).toList();
        if (!investmentIds.isEmpty()) {
            investmentEvents.deleteByInvestmentAccountIdIn(investmentIds);
            positionSnapshots.deleteByInvestmentAccountIdIn(investmentIds);
        }
        investmentAccounts.deleteAll(investmentOwned);
        List<FinancialAccountEntity> owned = accounts.findByConnectionId(connection.getId());
        List<UUID> transactionIds = owned.stream()
                .flatMap(account -> transactions.findAllByAccountId(account.getId()).stream())
                .map(TransactionEntity::getId)
                .toList();
        if (!transactionIds.isEmpty()) {
            transferMatches.deleteByTransactionIds(transactionIds);
        }
        for (FinancialAccountEntity account : owned) {
            transactions.deleteAll(transactions.findAllByAccountId(account.getId()));
            externalTransactions.deleteAll(externalTransactions.findAllByAccountId(account.getId()));
            balances.deleteAll(balances.findAllByAccountId(account.getId()));
        }
        accounts.deleteAll(owned);
        connection.setStatus("DISABLED");
        connections.save(connection);
        auditService.record(userId, "CONNECTION_PURGED", Map.of("connectionId", connectionId.toString()));
    }

    public ProviderConnectionEntity requireOwned(UUID userId, UUID connectionId) {
        return connections
                .findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
    }

    public FinancialAccountEntity upsertAccount(
            ProviderConnectionEntity connection, EnableBankingModels.ProviderAccount account) {
        if (account == null) {
            return null;
        }
        String uid = blankToNull(account.uid());
        String hash = blankToNull(account.identificationHash());
        FinancialAccountEntity entity = null;
        if (hash != null) {
            entity = accounts.findByConnectionIdAndIdentificationHashAndActiveIsTrue(connection.getId(), hash)
                    .orElse(null);
        }
        if (entity == null && uid != null) {
            entity = accounts.findByConnectionIdAndProviderAccountAliasAndActiveIsTrue(connection.getId(), uid)
                    .orElse(null);
        }
        if (entity == null) {
            if (uid == null) {
                return null;
            }
            entity = new FinancialAccountEntity();
            entity.setUserId(connection.getUserId());
            entity.setConnectionId(connection.getId());
            entity.setProviderAccountAlias(uid);
            entity.setIdentificationHash(hash);
            entity.setType(aisAccountType(account.cashAccountType()));
            entity.setDisplayName(blankToNull(account.name()) == null ? "Account" : account.name());
            entity.setCurrency(account.currency() == null || account.currency().isBlank()
                    ? "EUR"
                    : account.currency().toUpperCase());
            entity.setMaskedIdentifier(BankingMappings.maskIdentifier(account.iban()));
            entity.setActive(true);
            return accounts.save(entity);
        }
        if (uid != null) {
            entity.setProviderAccountAlias(uid);
        }
        if (hash != null) {
            entity.setIdentificationHash(hash);
        }
        if (account.cashAccountType() != null && !account.cashAccountType().isBlank()) {
            entity.setType(aisAccountType(account.cashAccountType()));
        }
        if (blankToNull(account.name()) != null) {
            entity.setDisplayName(account.name());
        }
        if (account.currency() != null && !account.currency().isBlank()) {
            entity.setCurrency(account.currency().toUpperCase());
        }
        if (account.iban() != null) {
            entity.setMaskedIdentifier(BankingMappings.maskIdentifier(account.iban()));
        }
        entity.setActive(true);
        return accounts.save(entity);
    }

    private static String aisAccountType(String cashAccountType) {
        String type = BankingMappings.accountType(cashAccountType);
        return "BROKERAGE".equals(type) ? "OTHER" : type;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void revokeSessionQuietly(ProviderConnectionEntity connection) {
        byte[] encrypted = connection.getExternalSessionIdEncrypted();
        if (encrypted == null) {
            return;
        }
        try {
            gateway.deleteSession(payloadCrypto.decryptUtf8(encrypted));
        } catch (RuntimeException ignored) {
            // disconnect still succeeds locally
        }
    }

    private String randomState() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
