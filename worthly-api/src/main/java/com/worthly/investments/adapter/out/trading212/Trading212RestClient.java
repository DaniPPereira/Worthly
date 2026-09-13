package com.worthly.investments.adapter.out.trading212;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.investments.application.RateLimitHeaders;
import com.worthly.investments.application.Trading212Gateway;
import com.worthly.investments.application.Trading212Models;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
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
public class Trading212RestClient implements Trading212Gateway {

    private static final Logger log = LoggerFactory.getLogger(Trading212RestClient.class);
    private static final Duration RATE_LIMIT_FALLBACK = Duration.ofMinutes(1);

    private final WorthlyProperties.Trading212 properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public Trading212RestClient(WorthlyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties.getTrading212();
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public boolean credentialsPresent() {
        return properties.credentialsPresent();
    }

    @Override
    public Trading212Models.AccountSummary accountSummary() {
        JsonNode root = get("/equity/account/summary");
        String accountId = firstNonBlank(text(root, "id"), "trading212");
        String currency = firstNonBlank(text(root, "currency"), text(root, "currencyCode"), "EUR").toUpperCase();
        JsonNode cashNode = root.path("cash");
        BigDecimal cash = sum(
                decimal(cashNode, "availableToTrade"),
                decimal(cashNode, "inPies"),
                decimal(cashNode, "reservedForOrders"),
                decimal(cashNode, "free"));
        if (cash.compareTo(BigDecimal.ZERO) == 0) {
            cash = firstNonNull(decimal(cashNode, "total"), BigDecimal.ZERO);
        }
        JsonNode investments = root.path("investments");
        BigDecimal portfolio = firstNonNull(
                decimal(investments, "currentValue"), decimal(cashNode, "invested"), decimal(root, "totalValue"));
        return new Trading212Models.AccountSummary(accountId, currency, cash, portfolio, Instant.now());
    }

    @Override
    public List<Trading212Models.Position> positions() {
        JsonNode root = get("/equity/positions");
        JsonNode items = root.isArray() ? root : root.path("items");
        List<Trading212Models.Position> positions = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode node : items) {
                positions.add(toPosition(node));
            }
        }
        return positions;
    }

    @Override
    public Trading212Models.HistoryPage transactions(String nextPagePath) {
        JsonNode root = get(nextPagePath == null ? "/equity/history/transactions?limit=50" : nextPagePath);
        return historyPage(root, false);
    }

    @Override
    public Trading212Models.HistoryPage dividends(String nextPagePath) {
        JsonNode root = get(nextPagePath == null ? "/equity/history/dividends?limit=50" : nextPagePath);
        return historyPage(root, true);
    }

    private Trading212Models.HistoryPage historyPage(JsonNode root, boolean dividend) {
        List<Trading212Models.HistoryEvent> events = new ArrayList<>();
        JsonNode items = root.path("items");
        if (items.isArray()) {
            for (JsonNode node : items) {
                events.add(dividend ? toDividend(node) : toTransaction(node));
            }
        }
        String next = text(root, "nextPagePath");
        return new Trading212Models.HistoryPage(events, next);
    }

    private Trading212Models.Position toPosition(JsonNode node) {
        JsonNode instrument = node.path("instrument");
        String ticker = firstNonBlank(text(instrument, "ticker"), text(node, "ticker"));
        JsonNode wallet = node.path("walletImpact");
        BigDecimal marketValue = firstNonNull(
                decimal(wallet, "currentValue"),
                multiply(decimal(node, "quantity"), decimal(node, "currentPrice")),
                decimal(node, "currentPrice"));
        String currency = firstNonBlank(
                text(wallet, "currency"), text(instrument, "currency"), text(node, "currency"), "EUR");
        return new Trading212Models.Position(
                ticker == null ? "unknown" : ticker,
                ticker,
                firstNonNull(decimal(node, "quantity"), BigDecimal.ZERO),
                firstNonNull(decimal(node, "averagePricePaid"), decimal(node, "averagePrice")),
                marketValue,
                currency.toUpperCase());
    }

    private Trading212Models.HistoryEvent toTransaction(JsonNode node) {
        String type = firstNonBlank(text(node, "type"), "TRANSFER");
        return new Trading212Models.HistoryEvent(
                firstNonBlank(text(node, "reference"), text(node, "id"), "tx-" + node.hashCode()),
                type,
                firstNonNull(decimal(node, "amount"), BigDecimal.ZERO),
                firstNonBlank(text(node, "currency"), "EUR"),
                instant(node, "dateTime"),
                null,
                null);
    }

    private Trading212Models.HistoryEvent toDividend(JsonNode node) {
        JsonNode instrument = node.path("instrument");
        String ticker = firstNonBlank(text(instrument, "ticker"), text(node, "ticker"));
        return new Trading212Models.HistoryEvent(
                firstNonBlank(text(node, "reference"), text(node, "id"), "div-" + node.hashCode()),
                "DIVIDEND",
                firstNonNull(decimal(node, "amount"), BigDecimal.ZERO),
                firstNonBlank(text(node, "currency"), "EUR"),
                instant(node, "paidOn"),
                ticker,
                decimal(node, "quantity"));
    }

    private JsonNode get(String path) {
        WorthlyProperties.Trading212.Credentials credentials = properties.credentials();
        if (credentials == null) {
            throw new ProviderException(503, "configuration_required");
        }
        String uri = resolve(path);
        try {
            ResponseEntity<String> response = restClient
                    .get()
                    .uri(URI.create(uri))
                    .headers(headers -> headers.setBasicAuth(credentials.key(), credentials.secret()))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(String.class);
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.info("Trading 212 {} returned status {}", path.startsWith("http") ? "page" : path, status);
            if (status == 429) {
                throw new RateLimitedException(RateLimitHeaders.retryAt(ex.getResponseHeaders(), RATE_LIMIT_FALLBACK));
            }
            throw new ProviderException(status, status == 401 || status == 403 ? "provider_unauthorized" : "provider_error");
        } catch (ProviderException | RateLimitedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderException(500, "provider_error");
        }
    }

    private String resolve(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = properties.getBaseUrl();
        if (path.startsWith("/api/")) {
            URI baseUri = URI.create(base);
            String origin = baseUri.getScheme() + "://" + baseUri.getAuthority();
            return origin + path;
        }
        if (path.startsWith("/")) {
            return trimSlash(base) + path;
        }
        return trimSlash(base) + "/" + path;
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() || "null".equals(text) ? null : text;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        if (text == null || text.isBlank() || "null".equals(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        boolean any = false;
        for (BigDecimal value : values) {
            if (value != null) {
                total = total.add(value);
                any = true;
            }
        }
        return any ? total : BigDecimal.ZERO;
    }

    private static BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return left.multiply(right);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return OffsetDateTime.parse(value).toInstant();
        }
    }
}
