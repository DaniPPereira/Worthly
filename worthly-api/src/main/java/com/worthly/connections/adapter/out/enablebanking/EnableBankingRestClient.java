package com.worthly.connections.adapter.out.enablebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.connections.application.EnableBankingGateway;
import com.worthly.connections.application.EnableBankingModels;
import com.worthly.infrastructure.config.WorthlyProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
        JsonNode root = get("/aspsps", "country", country, "psu_type", "personal");
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
    public EnableBankingModels.AuthStart startAuthorization(
            String aspspName, String country, String state, Instant validUntil) {
        String body =
                """
                {"access":{"balances":true,"transactions":true,"valid_until":"%s"},\
                "aspsp":{"name":%s,"country":%s},"state":%s,"redirect_url":%s,"psu_type":"personal"}
                """
                        .formatted(
                                validUntil.toString(),
                                jsonString(aspspName),
                                jsonString(country),
                                jsonString(state),
                                jsonString(properties.getCallbackUrl()));
        JsonNode root = post("/auth", body);
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
            String accountUid, LocalDate dateFrom, LocalDate dateTo, String continuationKey) {
        String path = "/accounts/" + accountUid + "/transactions?date_from=" + dateFrom + "&date_to=" + dateTo;
        if (continuationKey != null && !continuationKey.isBlank()) {
            path += "&continuation_key=" + continuationKey;
        }
        JsonNode root = get(path);
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
            String iban = node.path("account_id").path("iban").asText(null);
            accounts.add(new EnableBankingModels.ProviderAccount(
                    text(node, "uid"),
                    text(node, "identification_hash"),
                    text(node, "currency"),
                    firstNonBlank(text(node, "details"), text(node, "name"), "Account"),
                    text(node, "cash_account_type"),
                    iban));
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

    private EnableBankingModels.ProviderTransaction toTransaction(JsonNode node) {
        JsonNode amount = node.path("transaction_amount");
        String description = null;
        if (node.path("remittance_information").isArray() && node.path("remittance_information").size() > 0) {
            description = node.path("remittance_information").get(0).asText();
        }
        if (description == null || description.isBlank()) {
            description = text(node, "note");
        }
        String counterparty = firstNonBlank(
                node.path("creditor").path("name").asText(null), node.path("debtor").path("name").asText(null));
        return new EnableBankingModels.ProviderTransaction(
                emptyToNull(text(node, "transaction_id")),
                emptyToNull(text(node, "entry_reference")),
                text(node, "credit_debit_indicator"),
                text(node, "status"),
                decimal(amount, "amount"),
                text(amount, "currency"),
                localDate(node, "booking_date"),
                localDate(node, "value_date"),
                description,
                counterparty,
                node.toString());
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

    private void handleError(RestClientResponseException ex, String path) {
        int status = ex.getStatusCode().value();
        log.info("Enable Banking {} returned status {}", path, status);
        String code = "provider_error";
        try {
            JsonNode node = objectMapper.readTree(ex.getResponseBodyAsString());
            if (node.hasNonNull("error")) {
                code = node.get("error").asText();
            }
        } catch (Exception ignored) {
            // never log provider bodies
        }
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
