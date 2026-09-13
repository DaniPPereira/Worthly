package com.worthly.banking.accounts.adapter.in.web;

import com.worthly.banking.accounts.adapter.out.persistence.BalanceSnapshotEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.application.BankingMappings;
import com.worthly.banking.application.BankingQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final BankingQueryService queryService;

    public AccountController(BankingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return queryService.listAccounts(UUID.fromString(jwt.getSubject())).stream()
                .map(account -> AccountResponse.from(account, queryService.providerOf(account)))
                .toList();
    }

    @GetMapping("/{accountId}/balances")
    public List<BalanceResponse> balances(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        FinancialAccountEntity account = queryService.requireAccount(userId, accountId);
        List<BalanceSnapshotEntity> snapshots = queryService.listBalances(userId, accountId);
        String selected = selectLiquidBalanceType(account, snapshots);
        return snapshots.stream()
                .map(snapshot -> BalanceResponse.from(snapshot, snapshot.getBalanceType().equals(selected)))
                .toList();
    }

    private static String selectLiquidBalanceType(
            FinancialAccountEntity account, List<BalanceSnapshotEntity> snapshots) {
        if (account == null || !BankingMappings.includedInLiquidCash(account.getType()) || snapshots.isEmpty()) {
            return null;
        }
        return snapshots.stream()
                .min(Comparator.comparingInt(AccountController::liquidRank)
                        .thenComparing(BalanceSnapshotEntity::getObservedAt, Comparator.reverseOrder()))
                .map(BalanceSnapshotEntity::getBalanceType)
                .orElse(null);
    }

    private static int liquidRank(BalanceSnapshotEntity snapshot) {
        if (BankingMappings.isPreferredLiquidBalance(snapshot.getBalanceType())) {
            return 0;
        }
        if (BankingMappings.isBookedLiquidBalance(snapshot.getBalanceType())) {
            return 1;
        }
        return 9;
    }

    public record AccountResponse(
            UUID id,
            String provider,
            String displayName,
            String type,
            String currency,
            String maskedIdentifier,
            boolean active,
            boolean includedInLiquidCash,
            boolean includedInNetWorth) {
        static AccountResponse from(FinancialAccountEntity entity, String provider) {
            return new AccountResponse(
                    entity.getId(),
                    provider,
                    entity.getDisplayName(),
                    entity.getType(),
                    entity.getCurrency(),
                    entity.getMaskedIdentifier(),
                    entity.isActive(),
                    BankingMappings.includedInLiquidCash(entity.getType()),
                    BankingMappings.includedInNetWorth(entity.getType()));
        }
    }

    public record BalanceResponse(String type, MoneyResponse money, Instant observedAt, boolean usedForLiquidCash) {
        static BalanceResponse from(BalanceSnapshotEntity entity, boolean usedForLiquidCash) {
            return new BalanceResponse(
                    entity.getBalanceType(),
                    MoneyResponse.from(entity.getAmount(), entity.getCurrency()),
                    entity.getObservedAt(),
                    usedForLiquidCash);
        }
    }

    public record MoneyResponse(String amount, String currency) {
        static MoneyResponse from(BigDecimal amount, String currency) {
            return new MoneyResponse(amount.stripTrailingZeros().toPlainString(), currency);
        }
    }
}
