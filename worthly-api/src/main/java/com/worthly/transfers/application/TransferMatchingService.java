package com.worthly.transfers.application;

import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.application.TextNormalizer;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.categories.adapter.out.persistence.CategoryRepository;
import com.worthly.categories.application.CategorizationService;
import com.worthly.identity.application.OwnerService;
import com.worthly.infrastructure.security.TokenHashes;
import com.worthly.shared.web.ApiException;
import com.worthly.transfers.adapter.out.persistence.TransferMatchEntity;
import com.worthly.transfers.adapter.out.persistence.TransferMatchRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferMatchingService {

    private static final List<String> PURCHASE_HINTS =
            List.of("atm", "levantamento", "restaurant", "cafe", "supermarket", "uber", "taxi", "mbway");

    private final TransferMatchRepository matches;
    private final TransactionRepository transactions;
    private final FinancialAccountRepository accounts;
    private final CategoryRepository categories;
    private final CategorizationService categorization;
    private final OwnerService ownerService;

    public TransferMatchingService(
            TransferMatchRepository matches,
            TransactionRepository transactions,
            FinancialAccountRepository accounts,
            CategoryRepository categories,
            CategorizationService categorization,
            OwnerService ownerService) {
        this.matches = matches;
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.categorization = categorization;
        this.ownerService = ownerService;
    }

    @Transactional(readOnly = true)
    public List<TransferMatchEntity> list(UUID userId, String status) {
        if (status == null || status.isBlank()) {
            return matches.findByUserIdOrderByConfidenceDesc(userId);
        }
        return matches.findByUserIdAndStatus(userId, status);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UUID> linkedMatchIds(UUID userId, List<UUID> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> result = new HashMap<>();
        for (TransferMatchEntity match :
                matches.findByLeftTransactionIdInOrRightTransactionIdIn(transactionIds, transactionIds)) {
            if (!userId.equals(match.getUserId()) || !"LINKED".equals(match.getStatus())) {
                continue;
            }
            result.put(match.getLeftTransactionId(), match.getId());
            result.put(match.getRightTransactionId(), match.getId());
        }
        return result;
    }

    @Transactional
    public TransferMatchEntity linkManual(UUID userId, UUID leftId, UUID rightId) {
        if (leftId.equals(rightId)) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        TransactionEntity left = requireOwnedTx(userId, leftId);
        TransactionEntity right = requireOwnedTx(userId, rightId);
        String fingerprint = fingerprint(left.getId(), right.getId());
        if (matches.existsByPairFingerprintAndStatus(fingerprint, "REJECTED")) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "pair_rejected");
        }
        TransferMatchEntity entity = matches.findByPairFingerprintAndStatusIn(
                        fingerprint, List.of("LINKED", "SUGGESTED"))
                .orElseGet(TransferMatchEntity::new);
        UUID[] ordered = ordered(left.getId(), right.getId());
        entity.setUserId(userId);
        entity.setLeftTransactionId(ordered[0]);
        entity.setRightTransactionId(ordered[1]);
        entity.setConfidence(100);
        entity.setMethod("MANUAL");
        entity.setStatus("LINKED");
        entity.setPairFingerprint(fingerprint);
        matches.save(entity);
        Map<UUID, FinancialAccountEntity> accountById = accounts.findByUserIdOrderByDisplayNameAsc(userId).stream()
                .collect(Collectors.toMap(FinancialAccountEntity::getId, account -> account));
        applyLinkedClassification(left, right, accountById);
        return entity;
    }

    @Transactional
    public void reject(UUID userId, UUID matchId) {
        TransferMatchEntity entity = matches.findByIdAndUserId(matchId, userId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
        entity.setStatus("REJECTED");
        entity.setRejectedAt(Instant.now());
        matches.save(entity);
        recategorizeIfAutomatic(userId, entity.getLeftTransactionId());
        recategorizeIfAutomatic(userId, entity.getRightTransactionId());
    }

    @Transactional
    public void recalculate(UUID userId) {
        List<FinancialAccountEntity> owned = accounts.findByUserIdOrderByDisplayNameAsc(userId);
        List<UUID> accountIds = owned.stream().map(FinancialAccountEntity::getId).toList();
        if (accountIds.isEmpty()) {
            return;
        }
        Map<UUID, FinancialAccountEntity> accountById =
                owned.stream().collect(Collectors.toMap(FinancialAccountEntity::getId, account -> account));
        ZoneId zone = ZoneId.of(ownerService.require(userId).reportingTimezone());
        List<TransactionEntity> booked = transactions.findByAccountIdInAndLifecycleStatus(accountIds, "BOOKED");
        Map<UUID, TransactionEntity> byId =
                booked.stream().collect(Collectors.toMap(TransactionEntity::getId, tx -> tx));
        Set<UUID> reserved = new HashSet<>();
        Set<String> keep = new HashSet<>();
        for (TransferMatchEntity existing : matches.findByUserIdOrderByConfidenceDesc(userId)) {
            if ("REJECTED".equals(existing.getStatus())) {
                keep.add(existing.getPairFingerprint());
            } else if ("MANUAL".equals(existing.getMethod()) && "LINKED".equals(existing.getStatus())) {
                keep.add(existing.getPairFingerprint());
                reserved.add(existing.getLeftTransactionId());
                reserved.add(existing.getRightTransactionId());
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        Map<String, List<TransactionEntity>> grouped = booked.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCurrency() + "|" + tx.getAmount().stripTrailingZeros().toPlainString()));
        for (List<TransactionEntity> group : grouped.values()) {
            List<TransactionEntity> credits =
                    group.stream().filter(tx -> "CREDIT".equals(tx.getDirection())).toList();
            List<TransactionEntity> debits =
                    group.stream().filter(tx -> "DEBIT".equals(tx.getDirection())).toList();
            for (TransactionEntity credit : credits) {
                for (TransactionEntity debit : debits) {
                    if (credit.getAccountId().equals(debit.getAccountId())) {
                        continue;
                    }
                    long days = Math.abs(ChronoUnit.DAYS.between(date(credit, zone), date(debit, zone)));
                    if (days > 3) {
                        continue;
                    }
                    String fingerprint = fingerprint(credit.getId(), debit.getId());
                    if (matches.existsByPairFingerprintAndStatus(fingerprint, "REJECTED")) {
                        continue;
                    }
                    int confidence = TransferScore.score(
                            days,
                            hintsOwnedAccount(credit, debit, accountById),
                            isTrading212(credit, debit, accountById),
                            looksLikePurchase(credit) || looksLikePurchase(debit));
                    String status = TransferScore.autoStatus(confidence);
                    if (status == null) {
                        continue;
                    }
                    candidates.add(new Candidate(credit.getId(), debit.getId(), fingerprint, confidence, status));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::confidence).reversed());
        Set<UUID> used = new HashSet<>(reserved);
        for (Candidate candidate : candidates) {
            if (used.contains(candidate.leftId()) || used.contains(candidate.rightId())) {
                continue;
            }
            TransferMatchEntity entity = matches.findByPairFingerprintAndStatusIn(
                            candidate.fingerprint(), List.of("LINKED", "SUGGESTED"))
                    .orElseGet(TransferMatchEntity::new);
            if ("MANUAL".equals(entity.getMethod()) && "LINKED".equals(entity.getStatus())) {
                continue;
            }
            UUID[] ordered = ordered(candidate.leftId(), candidate.rightId());
            entity.setUserId(userId);
            entity.setLeftTransactionId(ordered[0]);
            entity.setRightTransactionId(ordered[1]);
            entity.setConfidence(candidate.confidence());
            entity.setMethod("AUTO");
            entity.setStatus(candidate.status());
            entity.setPairFingerprint(candidate.fingerprint());
            matches.save(entity);
            keep.add(candidate.fingerprint());
            used.add(candidate.leftId());
            used.add(candidate.rightId());
            if ("LINKED".equals(candidate.status())) {
                applyLinkedClassification(
                        byId.get(candidate.leftId()), byId.get(candidate.rightId()), accountById);
            }
        }
        for (TransferMatchEntity existing : List.copyOf(matches.findByUserIdOrderByConfidenceDesc(userId))) {
            if ("AUTO".equals(existing.getMethod()) && !keep.contains(existing.getPairFingerprint())) {
                matches.delete(existing);
            }
        }
    }

    private void recategorizeIfAutomatic(UUID userId, UUID transactionId) {
        transactions.findById(transactionId).ifPresent(tx -> {
            if ("MANUAL".equals(tx.getCategorizationSource())) {
                return;
            }
            tx.setCategoryId(null);
            tx.setCategorizationSource("UNCATEGORIZED");
            categorization.applyAutomatic(userId, tx);
            transactions.save(tx);
        });
    }

    private void applyLinkedClassification(
            TransactionEntity left, TransactionEntity right, Map<UUID, FinancialAccountEntity> accounts) {
        FinancialAccountEntity leftAccount = left == null ? null : accounts.get(left.getAccountId());
        FinancialAccountEntity rightAccount = right == null ? null : accounts.get(right.getAccountId());
        boolean leftBroker = leftAccount != null && "BROKERAGE".equals(leftAccount.getType());
        boolean rightBroker = rightAccount != null && "BROKERAGE".equals(rightAccount.getType());
        if (leftBroker ^ rightBroker) {
            TransactionEntity bank = leftBroker ? right : left;
            TransactionEntity broker = leftBroker ? left : right;
            boolean deposit = broker != null
                    && "CREDIT".equals(broker.getDirection())
                    && bank != null
                    && "DEBIT".equals(bank.getDirection());
            String code = deposit ? "transfer.investment_funding" : "transfer.investment_withdrawal";
            assignCategory(bank, code);
            assignCategory(broker, code);
            return;
        }
        applyInternalTransfer(left, right);
    }

    private void assignCategory(TransactionEntity transaction, String code) {
        if (transaction == null || "MANUAL".equals(transaction.getCategorizationSource())) {
            return;
        }
        categories.findByCode(code).ifPresent(category -> {
            transaction.setCategoryId(category.getId());
            transaction.setEconomicType(categorization.economicTypeFor(category));
            transaction.setCategorizationSource("RULE");
            transactions.save(transaction);
        });
    }

    private void applyInternalTransfer(TransactionEntity left, TransactionEntity right) {
        categories.findByCode("transfer.internal").ifPresent(category -> {
            if (left != null && !"MANUAL".equals(left.getCategorizationSource())) {
                left.setCategoryId(category.getId());
                left.setEconomicType(categorization.economicTypeFor(category));
                left.setCategorizationSource("RULE");
                transactions.save(left);
            }
            if (right != null && !"MANUAL".equals(right.getCategorizationSource())) {
                right.setCategoryId(category.getId());
                right.setEconomicType(categorization.economicTypeFor(category));
                right.setCategorizationSource("RULE");
                transactions.save(right);
            }
        });
    }

    private TransactionEntity requireOwnedTx(UUID userId, UUID transactionId) {
        TransactionEntity tx = transactions
                .findById(transactionId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
        FinancialAccountEntity account = accounts
                .findById(tx.getAccountId())
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
        if (!userId.equals(account.getUserId())) {
            throw ApiException.of(HttpStatus.NOT_FOUND, "not_found");
        }
        return tx;
    }

    private static boolean isTrading212(
            TransactionEntity left, TransactionEntity right, Map<UUID, FinancialAccountEntity> accounts) {
        return isBrokerage(accounts.get(left.getAccountId())) || isBrokerage(accounts.get(right.getAccountId()));
    }

    private static boolean isBrokerage(FinancialAccountEntity account) {
        return account != null && "BROKERAGE".equals(account.getType());
    }

    private static boolean hintsOwnedAccount(
            TransactionEntity left, TransactionEntity right, Map<UUID, FinancialAccountEntity> accounts) {
        FinancialAccountEntity leftAccount = accounts.get(left.getAccountId());
        FinancialAccountEntity rightAccount = accounts.get(right.getAccountId());
        String blob = TextNormalizer.normalize(String.join(
                " ",
                nullToEmpty(left.getMerchant()),
                nullToEmpty(left.getDescription()),
                nullToEmpty(right.getMerchant()),
                nullToEmpty(right.getDescription())));
        if (blob.contains("transfer") || blob.contains("transf") || blob.contains("own account")) {
            return true;
        }
        return containsAccountHint(blob, leftAccount) || containsAccountHint(blob, rightAccount);
    }

    private static boolean containsAccountHint(String blob, FinancialAccountEntity account) {
        if (account == null) {
            return false;
        }
        String name = TextNormalizer.normalize(account.getDisplayName());
        String masked = TextNormalizer.normalize(account.getMaskedIdentifier());
        return (!name.isEmpty() && blob.contains(name)) || (!masked.isEmpty() && blob.contains(masked));
    }

    private static boolean looksLikePurchase(TransactionEntity tx) {
        String blob = TextNormalizer.normalize(nullToEmpty(tx.getMerchant()) + " " + nullToEmpty(tx.getDescription()));
        return PURCHASE_HINTS.stream().anyMatch(blob::contains);
    }

    private static LocalDate date(TransactionEntity tx, ZoneId zone) {
        return tx.getReportingAt().atZone(zone).toLocalDate();
    }

    static String fingerprint(UUID left, UUID right) {
        UUID[] ordered = ordered(left, right);
        return TokenHashes.sha256(ordered[0] + ":" + ordered[1]);
    }

    private static UUID[] ordered(UUID left, UUID right) {
        return left.toString().compareTo(right.toString()) <= 0 ? new UUID[] {left, right} : new UUID[] {right, left};
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Candidate(UUID leftId, UUID rightId, String fingerprint, int confidence, String status) {}
}
