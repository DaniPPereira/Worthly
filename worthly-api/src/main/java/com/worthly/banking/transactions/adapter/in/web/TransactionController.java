package com.worthly.banking.transactions.adapter.in.web;

import com.worthly.banking.application.BankingQueryService;
import com.worthly.banking.application.TransactionQuery;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final BankingQueryService queryService;

    public TransactionController(BankingQueryService queryService) {
        this.queryService = queryService;
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
        int bounded = Math.min(Math.max(size, 1), 200);
        TransactionQuery query = new TransactionQuery(
                accountId, from, to, categoryId, economicType, direction, lifecycleStatus, minAmount, maxAmount, text);
        Page<TransactionEntity> result = queryService.listTransactions(
                UUID.fromString(jwt.getSubject()),
                query,
                PageRequest.of(Math.max(page, 0), bounded, Sort.by("reportingAt").descending()));
        return new TransactionPageResponse(
                result.getContent().stream().map(TransactionResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
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
            String notes) {
        static TransactionResponse from(TransactionEntity entity) {
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
                    entity.getNotes());
        }
    }

    public record MoneyResponse(String amount, String currency) {}

    public record TransactionPageResponse(List<TransactionResponse> items, int page, int size, long total) {}
}
