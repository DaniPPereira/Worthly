package com.worthly.connections.adapter.out.enablebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.connections.application.EnableBankingAuthSupport;
import com.worthly.connections.application.EnableBankingGateway;
import com.worthly.connections.application.EnableBankingModels;
import com.worthly.infrastructure.config.WorthlyProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EnableBankingRestClient implements EnableBankingGateway {

    private static final Logger log = LoggerFactory.getLogger(EnableBankingRestClient.class);
    private static final Duration RATE_LIMIT_FALLBACK = Duration.ofHours(6);

    private final WorthlyProperties.EnableBanking properties;
    private final EnableBankingJwtSigner jwtSigner;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public EnableBankingRestClient(
            WorthlyProperties properties, EnableBankingJwtSigner jwtSigner, ObjectMapper objectMapper) {
        this.properties = properties.getEnableBanking();
        this.jwtSigner = jwtSigner;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(this.properties.getBaseUrl()).build();
    }

    @Override
    public boolean configured() {
        return properties.isConfigured();
    }

    @Override
    public List<EnableBankingModels.DiscoveredBank> listAspsps(String country) {
        JsonNode root = (country == null || country.isBlank() || "*".equals(country))
                ? get("/aspsps", "psu_type", "personal")
                : get("/aspsps", "country", country, "psu_type", "personal");
        List<EnableBankingModels.DiscoveredBank> banks = new ArrayList<>();
        for (JsonNode node : root.path("aspsps")) {
            banks.add(new EnableBankingModels.DiscoveredBank(
                    text(node, "name"),
                    text(node, "country"),
                    text(node, "logo"),
                    node.path("maximum_consent_validity").asInt(0)));
        }
        return banks;
    }

    @Override
    public Optional<ApplicationInfo> application() {
        try {
            JsonNode root = get("/application");
            List<String> urls = new ArrayList<>();
            for (JsonNode node : root.path("redirect_urls")) {
                if (node.isTextual() && !node.asText().isBlank()) {
                    urls.add(node.asText());
                }
            }
            return Optional.of(new ApplicationInfo(List.copyOf(urls), text(root, "environment")));
        } catch (ProviderException | RateLimitedException ex) {
            log.info("Enable Banking /application unavailable; skipping redirect check");
            return Optional.empty();
        }
    }

    @Override
    public EnableBankingModels.AuthStart startAuthorization(
            String aspspName, String country, String state, Instant validUntil) {
        String validUntilText = EnableBankingAuthSupport.rfc3339Utc(validUntil);
        log.info("Enable Banking /auth aspsp={} country={} validUntil={}", aspspName, country, validUntilText);
        Map<String, Object> access = new LinkedHashMap<>();
        access.put("balances", true);
        access.put("transactions", true);
        access.put("valid_until", validUntilText);
        Map<String, Object> aspsp = new LinkedHashMap<>();
        aspsp.put("name", aspspName);
        aspsp.put("country", country);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access", access);
        body.put("aspsp", aspsp);
        body.put("state", state);
        body.put("redirect_url", properties.getCallbackUrl());
        body.put("psu_type", "personal");
        JsonNode root = post("/auth", writeJson(body));
        return new EnableBankingModels.AuthStart(
                text(root, "url"), Instant.now().plus(properties.getAuthorizationTtl()));
    }

    @Override
    public EnableBankingModels.ProviderSession createSession(String code) {
        return toSession(post("/sessions", "{\"code\":%s}".formatted(jsonString(code))));
    }

    @Override
    public EnableBankingModels.ProviderSession getSession(String sessionId) {
        return toSession(get("/sessions/" + sessionId));
    }

    @Override
    public List<EnableBankingModels.ProviderBalance> listBalances(String accountUid) {
        JsonNode root = get("/accounts/" + accountUid + "/balances");
        List<EnableBankingModels.ProviderBalance> balances = new ArrayList<>();
        for (JsonNode node : root.path("balances")) {
            JsonNode amount = node.path("balance_amount");
            balances.add(new EnableBankingModels.ProviderBalance(
                    text(node, "balance_type"),
                    decimal(amount, "amount"),
                    text(amount, "currency"),
                    instantOrNow(node.path("last_change_date_time")),
                    localDate(node, "reference_date")));
        }
        return balances;
    }

    @Override
    public EnableBankingModels.TransactionPage listTransactions(
            String accountUid,
            LocalDate dateFrom,
            LocalDate dateTo,
            String continuationKey,
            String strategy) {
        JsonNode root = get(transactionsPath(accountUid, dateFrom, dateTo, continuationKey, strategy));
        List<EnableBankingModels.ProviderTransaction> transactions = new ArrayList<>();
        for (JsonNode node : root.path("transactions")) {
            transactions.add(toTransaction(node));
        }
        String continuation = root.path("continuation_key").isMissingNode() || root.path("continuation_key").isNull()
                ? null
                : root.path("continuation_key").asText();
        if (continuation != null && continuation.isBlank()) {
            continuation = null;
        }
        return new EnableBankingModels.TransactionPage(transactions, continuation);
    }

    private static String transactionsPath(
            String accountUid,
            LocalDate dateFrom,
            LocalDate dateTo,
            String continuationKey,
            String strategy) {
        StringBuilder path = new StringBuilder("/accounts/").append(accountUid).append("/transactions");
        List<String> query = new ArrayList<>();
        if (dateFrom != null) {
            query.add("date_from=" + dateFrom);
        }
        if (dateTo != null) {
            query.add("date_to=" + dateTo);
        }
        if (strategy != null && !strategy.isBlank()) {
            query.add("strategy=" + strategy);
        }
        if (continuationKey != null && !continuationKey.isBlank()) {
            query.add("continuation_key=" + continuationKey);
        }
        if (!query.isEmpty()) {
            path.append("?").append(String.join("&", query));
        }
        return path.toString();
    }

    @Override
    public void deleteSession(String sessionId) {
        try {
            client().delete().uri("/sessions/" + sessionId).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.info("Enable Banking session revoke returned status {}", ex.getStatusCode().value());
        }
    }

    private EnableBankingModels.ProviderSession toSession(JsonNode root) {
        List<EnableBankingModels.ProviderAccount> accounts = new ArrayList<>();
        for (JsonNode node : root.path("accounts")) {
            EnableBankingModels.ProviderAccount account = toAccount(node);
            if (account != null) {
                accounts.add(account);
            }
        }
        Instant expires = null;
        if (!root.path("access").path("valid_until").isMissingNode()
                && !root.path("access").path("valid_until").isNull()
                && !root.path("access").path("valid_until").asText().isBlank()) {
            expires = parseInstant(root.path("access").path("valid_until").asText());
        }
        return new EnableBankingModels.ProviderSession(
                text(root, "session_id"), text(root, "status"), expires, accounts);
    }

    private static EnableBankingModels.ProviderAccount toAccount(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            String uid = node.asText();
            if (uid == null || uid.isBlank()) {
                return null;
            }
            return new EnableBankingModels.ProviderAccount(uid.strip(), null, null, null, null, null);
        }
        if (!node.isObject()) {
            return null;
        }
        String uid = text(node, "uid");
        String hash = text(node, "identification_hash");
        if (uid == null && hash == null) {
            return null;
        }
        String iban = node.path("account_id").path("iban").asText(null);
        if (iban != null && iban.isBlank()) {
            iban = null;
        }
        return new EnableBankingModels.ProviderAccount(
                uid,
                hash,
                text(node, "currency"),
                firstNonBlank(text(node, "details"), text(node, "name")),
                text(node, "cash_account_type"),
                iban);
    }

    private EnableBankingModels.ProviderTransaction toTransaction(JsonNode node) {
        return EnableBankingTransactionMapper.from(node);
    }

    private JsonNode get(String path, String... query) {
        String uri = path;
        if (query.length >= 2) {
            StringBuilder builder = new StringBuilder(path);
            builder.append(path.contains("?") ? "&" : "?");
            for (int i = 0; i < query.length; i += 2) {
                if (i > 0) {
                    builder.append("&");
                }
                builder.append(query[i]).append("=").append(query[i + 1]);
            }
            uri = builder.toString();
        }
        String target = uri;
        return exchange(() -> client().get().uri(target).retrieve().toEntity(String.class), path);
    }

    private JsonNode post(String path, String json) {
        return exchange(
                () -> client()
                        .post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json)
                        .retrieve()
                        .toEntity(String.class),
                path);
    }

    private RestClient client() {
        return restClient.mutate().defaultHeader("Authorization", "Bearer " + jwtSigner.sign()).build();
    }

    private JsonNode exchange(EntityCall call, String path) {
        try {
            ResponseEntity<String> response = call.get();
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (RestClientResponseException ex) {
            handleError(ex, path);
            throw new ProviderException(ex.getStatusCode().value(), "provider_error");
        } catch (ProviderException | RateLimitedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderException(500, "provider_error");
        }
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new ProviderException(500, "provider_error");
        }
    }

    private void handleError(RestClientResponseException ex, String path) {
        int status = ex.getStatusCode().value();
        String code = "provider_error";
        String message = "";
        try {
            JsonNode node = objectMapper.readTree(ex.getResponseBodyAsString());
            if (node.hasNonNull("error")) {
                code = node.get("error").asText();
            }
            if (node.hasNonNull("message")) {
                message = node.get("message").asText();
                if (message.length() > 240) {
                    message = message.substring(0, 240);
                }
            }
        } catch (Exception ignored) {
            // never log raw provider bodies
        }
        log.info("Enable Banking {} returned status {} error={} message={}", path, status, code, message);
        if (status == 429 || "ASPSP_RATE_LIMIT_EXCEEDED".equals(code)) {
            String retryAfter =
                    ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
            throw new RateLimitedException(EnableBankingGateway.retryAtFrom(RATE_LIMIT_FALLBACK, retryAfter));
        }
        throw new ProviderException(status, code);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private static LocalDate localDate(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDate.parse(value);
    }

    private static Instant instantOrNow(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return Instant.now();
        }
        return parseInstant(node.asText());
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return OffsetDateTime.parse(value).toInstant();
        }
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @FunctionalInterface
    private interface EntityCall {
        ResponseEntity<String> get() throws Exception;
    }
}
