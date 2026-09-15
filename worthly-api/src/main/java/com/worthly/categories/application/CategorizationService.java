package com.worthly.categories.application;

import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.application.TextNormalizer;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.categories.adapter.out.persistence.CategorizationRuleEntity;
import com.worthly.categories.adapter.out.persistence.CategorizationRuleRepository;
import com.worthly.categories.adapter.out.persistence.CategoryEntity;
import com.worthly.categories.adapter.out.persistence.CategoryRepository;
import com.worthly.shared.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategorizationService {

    private final CategoryRepository categories;
    private final CategorizationRuleRepository rules;
    private final HeuristicCatalog heuristics;
    private final FinancialAccountRepository accounts;
    private final TransactionRepository transactions;

    public CategorizationService(
            CategoryRepository categories,
            CategorizationRuleRepository rules,
            HeuristicCatalog heuristics,
            FinancialAccountRepository accounts,
            TransactionRepository transactions) {
        this.categories = categories;
        this.rules = rules;
        this.heuristics = heuristics;
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public void applyAutomatic(UUID userId, TransactionEntity transaction) {
        if ("MANUAL".equals(transaction.getCategorizationSource())) {
            return;
        }
        for (CategorizationRuleEntity rule : rules.findByUserIdAndEnabledIsTrueOrderByPriorityAsc(userId)) {
            if (matches(rule, transaction)) {
                categories.findById(rule.getTargetCategoryId())
                        .ifPresent(category -> assign(transaction, category, "RULE"));
                return;
            }
        }
        String code = heuristicCode(transaction);
        if (code != null) {
            var heuristic = categories.findByCode(code);
            if (heuristic.isPresent()) {
                assign(transaction, heuristic.get(), "HEURISTIC");
                return;
            }
        }
        categories.findByCode("uncategorized")
                .ifPresent(category -> assign(transaction, category, "UNCATEGORIZED"));
    }

    @Transactional
    public void recategorizeNonManual(UUID userId) {
        List<UUID> accountIds = accounts.findByUserIdOrderByDisplayNameAsc(userId).stream()
                .map(FinancialAccountEntity::getId)
                .toList();
        for (UUID accountId : accountIds) {
            for (TransactionEntity transaction : transactions.findAllByAccountId(accountId)) {
                applyAutomatic(userId, transaction);
                transactions.save(transaction);
            }
        }
    }

    public String economicTypeFor(CategoryEntity category) {
        return economicTypeFor(category, null);
    }

    public String economicTypeFor(CategoryEntity category, String direction) {
        CategoryEntity current = category;
        while (current != null) {
            if ("uncategorized".equals(current.getCode())) {
                return "CREDIT".equals(direction) ? "INCOME" : "EXPENSE";
            }
            String mapped = mapCode(current.getCode());
            if (!"OTHER".equals(mapped)) {
                return mapped;
            }
            current = current.getParentId() == null ? null : categories.findById(current.getParentId()).orElse(null);
        }
        return "OTHER";
    }

    public CategoryEntity requireVisible(UUID userId, UUID categoryId) {
        CategoryEntity category = categories
                .findById(categoryId)
                .orElseThrow(() -> ApiException.of(HttpStatus.BAD_REQUEST, "invalid_category"));
        if (category.isSystem() || userId.equals(category.getUserId())) {
            return category;
        }
        throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_category");
    }

    public static String mapCode(String code) {
        if (code == null || code.isBlank()) {
            return "OTHER";
        }
        if (code.startsWith("income.")) {
            return "INCOME";
        }
        if (code.equals("expense.fees")) {
            return "FEE";
        }
        if (code.startsWith("expense.")) {
            return "EXPENSE";
        }
        if (code.equals("transfer.internal")) {
            return "INTERNAL_TRANSFER";
        }
        if (code.equals("transfer.investment_funding")) {
            return "INVESTMENT_FUNDING";
        }
        if (code.equals("transfer.investment_withdrawal")) {
            return "INVESTMENT_WITHDRAWAL";
        }
        return "OTHER";
    }

    private void assign(TransactionEntity transaction, CategoryEntity category, String source) {
        transaction.setCategoryId(category.getId());
        transaction.setCategorizationSource(source);
        transaction.setEconomicType(economicTypeFor(category, transaction.getDirection()));
    }

    private boolean matches(CategorizationRuleEntity rule, TransactionEntity transaction) {
        if (rule.getAmountMin() != null && transaction.getAmount().compareTo(rule.getAmountMin()) < 0) {
            return false;
        }
        if (rule.getAmountMax() != null && transaction.getAmount().compareTo(rule.getAmountMax()) > 0) {
            return false;
        }
        String expected = TextNormalizer.normalize(rule.getMatchValue());
        String actual = switch (rule.getField()) {
            case "MERCHANT" -> TextNormalizer.normalize(transaction.getMerchant());
            case "DESCRIPTION" -> TextNormalizer.normalize(transaction.getDescription());
            case "ACCOUNT_ID" -> transaction.getAccountId().toString();
            case "DIRECTION" -> transaction.getDirection();
            default -> "";
        };
        if ("DIRECTION".equals(rule.getField())) {
            return "EQUALS".equals(rule.getOperator()) && actual.equalsIgnoreCase(rule.getMatchValue().trim());
        }
        String haystack = actual;
        String needle = "ACCOUNT_ID".equals(rule.getField()) ? expected : expected;
        return switch (rule.getOperator()) {
            case "EQUALS" -> haystack.equals(needle);
            case "CONTAINS" -> haystack.contains(needle);
            case "STARTS_WITH" -> haystack.startsWith(needle);
            default -> false;
        };
    }

    private String heuristicCode(TransactionEntity transaction) {
        String merchant = TextNormalizer.normalize(transaction.getMerchant());
        String description = TextNormalizer.normalize(transaction.getDescription());
        for (HeuristicCatalog.Entry entry : heuristics.entries()) {
            String key = TextNormalizer.normalize(entry.key());
            if (key.isEmpty()) {
                continue;
            }
            if (merchant.equals(key) || description.equals(key)) {
                return entry.code();
            }
        }
        String longest = "";
        String code = null;
        for (HeuristicCatalog.Entry entry : heuristics.entries()) {
            String key = TextNormalizer.normalize(entry.key());
            if (key.isEmpty() || key.length() <= longest.length()) {
                continue;
            }
            if (!matchesHeuristic(merchant, key) && !matchesHeuristic(description, key)) {
                continue;
            }
            longest = key;
            code = entry.code();
        }
        return code;
    }

    private static boolean matchesHeuristic(String haystack, String key) {
        if (key.length() <= 4) {
            return isWholeToken(haystack, key);
        }
        return haystack.contains(key);
    }

    private static boolean isWholeToken(String haystack, String key) {
        int from = 0;
        while (from <= haystack.length() - key.length()) {
            int index = haystack.indexOf(key, from);
            if (index < 0) {
                return false;
            }
            boolean before = index == 0 || !Character.isLetterOrDigit(haystack.charAt(index - 1));
            int end = index + key.length();
            boolean after = end == haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (before && after) {
                return true;
            }
            from = index + 1;
        }
        return false;
    }
}
