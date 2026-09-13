package com.worthly.banking.transactions.adapter.in.web;

import com.worthly.banking.application.BankingQueryService;
import com.worthly.banking.application.TransactionQuery;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exports")
public class TransactionExportController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv");

    private final BankingQueryService queryService;

    public TransactionExportController(BankingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping(value = "/transactions.csv", produces = "text/csv")
    public ResponseEntity<String> csv(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String economicType,
            @RequestParam(required = false) String direction,
            @RequestParam(name = "status", required = false) String lifecycleStatus,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(name = "q", required = false) String text) {
        TransactionQuery query = new TransactionQuery(
                accountId, from, to, categoryId, economicType, direction, lifecycleStatus, minAmount, maxAmount, text);
        StringBuilder csv = new StringBuilder();
        csv.append("id,accountId,reportingAt,direction,lifecycleStatus,economicType,")
                .append("amount,currency,merchant,description,categoryCode,categoryLabel,notes,transferMatchId\n");
        for (TransactionEntity tx : queryService.listTransactionsForExport(UUID.fromString(jwt.getSubject()), query)) {
            csv.append(row(tx)).append('\n');
        }
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions.csv\"")
                .body(csv.toString());
    }

    private static String row(TransactionEntity tx) {
        return String.join(
                ",",
                csv(tx.getId()),
                csv(tx.getAccountId()),
                csv(tx.getReportingAt() == null ? "" : tx.getReportingAt().atOffset(ZoneOffset.UTC).toString()),
                csv(tx.getDirection()),
                csv(tx.getLifecycleStatus()),
                csv(tx.getEconomicType()),
                csv(tx.getAmount() == null ? "" : tx.getAmount().stripTrailingZeros().toPlainString()),
                csv(tx.getCurrency()),
                csv(tx.getMerchant()),
                csv(tx.getDescription()),
                "",
                "",
                csv(tx.getNotes()),
                "");
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
