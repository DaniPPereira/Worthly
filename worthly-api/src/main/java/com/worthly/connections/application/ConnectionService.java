package com.worthly.connections.application;

import com.worthly.audit.application.AuditService;
import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotRepository;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.application.BankingMappings;
import com.worthly.banking.transactions.adapter.out.persistence.ExternalTransactionRepository;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.connections.adapter.out.persistence.AuthorizationAttemptEntity;
import com.worthly.connections.adapter.out.persistence.AuthorizationAttemptRepository;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.crypto.PayloadCrypto;
import com.worthly.infrastructure.security.TokenHashes;
import com.worthly.shared.web.ApiException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ConnectionService {

    private static final String PROVIDER = "ENABLE_BANKING";
    private final EnableBankingGateway gateway;
    private final EnableBankingDiscoveryService discoveryService;
    private final AuthorizationAttemptRepository attempts;
    private final ProviderConnectionRepository connections;
    private final FinancialAccountRepository accounts;
    private final BalanceSnapshotRepository balances;
    private final ExternalTransactionRepository externalTransactions;
    private final TransactionRepository transactions;
    private final PayloadCrypto payloadCrypto;
    private final AuditService auditService;
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
            PayloadCrypto payloadCrypto,
            AuditService auditService,
            WorthlyProperties properties) {
        this.gateway = gateway;
        this.discoveryService = discoveryService;
        this.attempts = attempts;
        this.connections = connections;
        this.accounts = accounts;
        this.balances = balances;
        this.externalTransactions = externalTransactions;
        this.transactions = transactions;
        this.payloadCrypto = payloadCrypto;
        this.auditService = auditService;
        this.properties = properties.getEnableBanking();
    }

    @Transactional(readOnly = true)
    public List<ProviderConnectionEntity> list(UUID userId) {
        return connections.findByUserIdOrderByUpdatedAtDesc(userId);
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
        String state = randomState();
        Instant expiresAt = Instant.now().plus(properties.getAuthorizationTtl());
        Instant validUntil = Instant.now().plusSeconds(Math.max(bank.maximumConsentValiditySeconds(), 90L * 24 * 3600));
        if (bank.maximumConsentValiditySeconds() > 0) {
            validUntil = Instant.now().plusSeconds(bank.maximumConsentValiditySeconds());
        }
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
        for (EnableBankingModels.ProviderAccount account : session.accounts()) {
            upsertAccount(connection, account);
        }
        auditService.record(
                attempt.getUserId(),
                "ENABLE_BANKING_CONNECTED",
                Map.of("connectionId", connection.getId().toString(), "aspspName", attempt.getAspspName()));
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
        List<FinancialAccountEntity> owned = accounts.findByConnectionId(connection.getId());
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
        FinancialAccountEntity entity = null;
        if (account.identificationHash() != null && !account.identificationHash().isBlank()) {
            entity = accounts.findByConnectionIdAndIdentificationHashAndActiveIsTrue(
                            connection.getId(), account.identificationHash())
                    .orElse(null);
        }
        if (entity == null && account.uid() != null) {
            entity = accounts.findByConnectionIdAndProviderAccountAliasAndActiveIsTrue(
                            connection.getId(), account.uid())
                    .orElse(null);
        }
        if (entity == null) {
            entity = new FinancialAccountEntity();
            entity.setUserId(connection.getUserId());
            entity.setConnectionId(connection.getId());
        }
        entity.setProviderAccountAlias(account.uid());
        entity.setIdentificationHash(account.identificationHash());
        entity.setType(BankingMappings.accountType(account.cashAccountType()));
        entity.setDisplayName(account.name() == null ? "Account" : account.name());
        entity.setCurrency(account.currency() == null ? "EUR" : account.currency().toUpperCase());
        entity.setMaskedIdentifier(BankingMappings.maskIdentifier(account.iban()));
        entity.setActive(true);
        return accounts.save(entity);
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
