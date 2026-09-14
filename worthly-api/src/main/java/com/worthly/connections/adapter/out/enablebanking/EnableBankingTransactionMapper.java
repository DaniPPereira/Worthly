package com.worthly.connections.adapter.out.enablebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.worthly.banking.application.BankingMappings;
import com.worthly.connections.application.EnableBankingModels;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class EnableBankingTransactionMapper {

    private EnableBankingTransactionMapper() {}

    public static EnableBankingModels.ProviderTransaction from(JsonNode node) {
        JsonNode amount = node.path("transaction_amount");
        String indicator = text(node, "credit_debit_indicator");
        boolean credit = "CREDIT".equals(BankingMappings.direction(indicator));
        JsonNode party = credit ? node.path("debtor") : node.path("creditor");
        JsonNode other = credit ? node.path("creditor") : node.path("debtor");
        List<String> remittance = remittanceLines(node.path("remittance_information"));
        String counterparty = firstNonBlank(text(party, "name"), text(other, "name"));
        if (counterparty == null && !remittance.isEmpty()) {
            counterparty = remittance.get(0);
        }
        String additional = firstNonBlank(text(node, "additional_information"), text(node, "note"));
        String description = joinDistinct(remittance, additional);
        String location = firstNonBlank(formatAddress(party.path("postal_address")), formatAddress(other.path("postal_address")));
        return new EnableBankingModels.ProviderTransaction(
                emptyToNull(text(node, "transaction_id")),
                emptyToNull(text(node, "entry_reference")),
                indicator,
                text(node, "status"),
                decimal(amount, "amount"),
                text(amount, "currency"),
                localDate(node, "booking_date"),
                localDate(node, "value_date"),
                emptyToNull(description),
                emptyToNull(counterparty),
                emptyToNull(location),
                node.toString());
    }

    static String formatAddress(JsonNode address) {
        if (address == null || address.isMissingNode() || address.isNull() || !address.isObject()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        JsonNode lines = address.path("address_line");
        if (lines.isArray()) {
            for (JsonNode line : lines) {
                addPart(parts, line.asText(null));
            }
        }
        addPart(parts, joinWords(text(address, "street_name"), text(address, "building_number")));
        addPart(parts, joinWords(text(address, "post_code"), text(address, "town_name")));
        addPart(parts, text(address, "country"));
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static List<String> remittanceLines(JsonNode node) {
        List<String> lines = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                addPart(lines, item.asText(null));
            }
        }
        return lines;
    }

    private static String joinDistinct(List<String> remittance, String additional) {
        Set<String> seen = new LinkedHashSet<>();
        for (String line : remittance) {
            seen.add(line);
        }
        addPart(seen, additional);
        return seen.isEmpty() ? null : String.join(" · ", seen);
    }

    private static void addPart(List<String> parts, String value) {
        String trimmed = emptyToNull(value);
        if (trimmed != null && parts.stream().noneMatch(existing -> existing.equalsIgnoreCase(trimmed))) {
            parts.add(trimmed);
        }
    }

    private static void addPart(Set<String> parts, String value) {
        String trimmed = emptyToNull(value);
        if (trimmed != null) {
            parts.add(trimmed);
        }
    }

    private static String joinWords(String left, String right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + " " + right;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text.trim();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private static LocalDate localDate(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDate.parse(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
