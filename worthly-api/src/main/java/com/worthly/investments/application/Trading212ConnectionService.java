package com.worthly.investments.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.audit.application.AuditService;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.crypto.PayloadCrypto;
import com.worthly.notifications.application.NotificationService;
import com.worthly.shared.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Trading212ConnectionService {

    public static final String PROVIDER = "TRADING_212";

    private static final Logger log = LoggerFactory.getLogger(Trading212ConnectionService.class);

    private final ProviderConnectionRepository connections;
    private final Trading212Gateway gateway;
    private final NotificationService notifications;
    private final PayloadCrypto crypto;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final WorthlyProperties properties;

    public Trading212ConnectionService(
            ProviderConnectionRepository connections,
            Trading212Gateway gateway,
            NotificationService notifications,
            PayloadCrypto crypto,
            ObjectMapper objectMapper,
            AuditService auditService,
            WorthlyProperties properties) {
        this.connections = connections;
        this.gateway = gateway;
        this.notifications = notifications;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional
    public ProviderConnectionEntity connect(UUID userId, String apiKey, String apiSecret, String environment) {
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        String key = apiKey.strip();
        String secret = apiSecret.strip();
        ProviderConnectionEntity connection =
                connections.findByUserIdAndProvider(userId, PROVIDER).orElseGet(() -> newConnection(userId));
        List<String> bases = candidateBases(environment);
        Trading212Gateway.ProviderException last = null;
        for (String base : bases) {
            Trading212CredentialContext.set(new Trading212CredentialContext.Bound(key, secret, base));
            try {
                gateway.accountSummary();
                connection.setCredentialsEncrypted(encrypt(key, secret, base));
                connection.setProvider(PROVIDER);
                connection.setAspspName(labelFor(base));
                connection.setStatus("ACTIVE");
                connection.setLastErrorCode(null);
                ProviderConnectionEntity saved = connections.save(connection);
                auditService.record(
                        userId, "TRADING_212_CONNECTED", Map.of("status", saved.getStatus(), "environment", environmentOf(base)));
                return saved;
            } catch (Trading212Gateway.ProviderException ex) {
                last = ex;
                log.info("Trading 212 credential check against {} failed with status {}", environmentOf(base), ex.status());
                if (ex.status() != 401 && ex.status() != 403) {
                    break;
                }
            } finally {
                Trading212CredentialContext.clear();
            }
        }
        connection.setCredentialsEncrypted(encrypt(key, secret, bases.getFirst()));
        connection.setAspspName("Trading 212");
        connection.setStatus("ERROR");
        connection.setLastErrorCode(last == null ? "provider_error" : last.code());
        ProviderConnectionEntity saved = connections.save(connection);
        auditService.record(userId, "TRADING_212_CONNECTED", Map.of("status", saved.getStatus()));
        return saved;
    }

    @Transactional
    public void reconcile() {
        for (ProviderConnectionEntity connection : connections.findByProvider(PROVIDER)) {
            if ("DISABLED".equals(connection.getStatus())) {
                continue;
            }
            if (hasStoredCredentials(connection) || gateway.credentialsPresent()) {
                continue;
            }
            markConfigurationRequired(connection.getUserId(), connection);
        }
    }

    public Optional<Trading212CredentialContext.Bound> credentialsFor(ProviderConnectionEntity connection) {
        if (!hasStoredCredentials(connection)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(crypto.decryptUtf8(connection.getCredentialsEncrypted()));
            String key = text(root, "key");
            String secret = text(root, "secret");
            if (key == null || secret == null) {
                return Optional.empty();
            }
            String base = text(root, "baseUrl");
            if (base == null) {
                base = properties.getTrading212().getBaseUrl();
            }
            return Optional.of(new Trading212CredentialContext.Bound(key, secret, base));
        } catch (Exception ex) {
            log.info("Trading 212 stored credentials could not be read");
            return Optional.empty();
        }
    }

    private List<String> candidateBases(String environment) {
        String configured = properties.getTrading212().getBaseUrl();
        if (!Trading212CredentialContext.isOfficialHost(configured)) {
            return List.of(configured);
        }
        if ("DEMO".equalsIgnoreCase(environment)) {
            return List.of(Trading212CredentialContext.DEMO_BASE);
        }
        return List.of(Trading212CredentialContext.LIVE_BASE);
    }

    private byte[] encrypt(String apiKey, String apiSecret, String baseUrl) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("key", apiKey);
            payload.put("secret", apiSecret);
            payload.put("baseUrl", baseUrl);
            return crypto.encryptUtf8(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to encode Trading 212 credentials", ex);
        }
    }

    private static boolean hasStoredCredentials(ProviderConnectionEntity connection) {
        return connection.getCredentialsEncrypted() != null && connection.getCredentialsEncrypted().length > 0;
    }

    private void markConfigurationRequired(UUID userId, ProviderConnectionEntity connection) {
        connection.setStatus("CONFIGURATION_REQUIRED");
        connection.setLastErrorCode("missing_credentials");
        connections.save(connection);
        notifications.remind(userId, "CONFIGURATION_REQUIRED");
    }

    private static ProviderConnectionEntity newConnection(UUID userId) {
        ProviderConnectionEntity created = new ProviderConnectionEntity();
        created.setUserId(userId);
        created.setProvider(PROVIDER);
        created.setAspspName("Trading 212");
        created.setStatus("CONFIGURATION_REQUIRED");
        return created;
    }

    private static String labelFor(String baseUrl) {
        if (baseUrl != null && baseUrl.contains("demo.trading212.com")) {
            return "Trading 212 Practice";
        }
        return "Trading 212";
    }

    private static String environmentOf(String baseUrl) {
        if (baseUrl != null && baseUrl.contains("demo.trading212.com")) {
            return "DEMO";
        }
        if (baseUrl != null && baseUrl.contains("live.trading212.com")) {
            return "LIVE";
        }
        return "CUSTOM";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
