package com.worthly.banking.transactions.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.worthly.banking.application.BankingQueryService;
import com.worthly.banking.application.TransactionQuery;
import com.worthly.banking.application.TransactionWriteService;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.transfers.application.TransferMatchingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final BankingQueryService queryService;
    private final TransactionWriteService writeService;
    private final TransferMatchingService matching;

    public TransactionController(
            BankingQueryService queryService,
            TransactionWriteService writeService,
            TransferMatchingService matching) {
        this.queryService = queryService;
        this.writeService = writeService;
        this.matching = matching;
    }

    @GetMapping
    public TransactionPageResponse list(
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
            @RequestParam(name = "q", required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        int bounded = Math.min(Math.max(size, 1), 200);
        TransactionQuery query = new TransactionQuery(
                accountId, from, to, categoryId, economicType, direction, lifecycleStatus, minAmount, maxAmount, text);
        Page<TransactionEntity> result = queryService.listTransactions(
                userId, query, PageRequest.of(Math.max(page, 0), bounded, Sort.by("reportingAt").descending()));
        Map<UUID, UUID> matchIds = matching.linkedMatchIds(
                userId, result.getContent().stream().map(TransactionEntity::getId).toList());
        return new TransactionPageResponse(
                result.getContent().stream()
                        .map(tx -> TransactionResponse.from(tx, matchIds.get(tx.getId())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @PatchMapping("/{transactionId}")
    public TransactionResponse patch(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId, @RequestBody JsonNode body) {
        UUID userId = UUID.fromString(jwt.getSubject());
        TransactionEntity updated = writeService.patch(userId, transactionId, body);
        Map<UUID, UUID> matchIds = matching.linkedMatchIds(userId, List.of(updated.getId()));
        return TransactionResponse.from(updated, matchIds.get(updated.getId()));
    }

    public record TransactionResponse(
            UUID id,
            UUID accountId,
            String direction,
            String lifecycleStatus,
            String economicType,
            MoneyResponse money,
            String merchant,
            String description,
            Instant reportingAt,
            UUID categoryId,
            String notes,
            UUID transferMatchId) {
        static TransactionResponse from(TransactionEntity entity, UUID transferMatchId) {
            return new TransactionResponse(
                    entity.getId(),
                    entity.getAccountId(),
                    entity.getDirection(),
                    entity.getLifecycleStatus(),
                    entity.getEconomicType(),
                    new MoneyResponse(entity.getAmount().stripTrailingZeros().toPlainString(), entity.getCurrency()),
                    entity.getMerchant(),
                    entity.getDescription(),
                    entity.getReportingAt(),
                    entity.getCategoryId(),
                    entity.getNotes(),
                    transferMatchId);
        }
    }

    public record MoneyResponse(String amount, String currency) {}

    public record TransactionPageResponse(List<TransactionResponse> items, int page, int size, long total) {}
}
