package com.worthly.categories.application;

import com.worthly.categories.adapter.out.persistence.CategorizationRuleEntity;
import com.worthly.categories.adapter.out.persistence.CategorizationRuleRepository;
import com.worthly.categories.adapter.out.persistence.CategoryEntity;
import com.worthly.categories.adapter.out.persistence.CategoryRepository;
import com.worthly.shared.web.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryAdminService {

    private static final Set<String> FIELDS = Set.of("MERCHANT", "DESCRIPTION", "ACCOUNT_ID", "DIRECTION");
    private static final Set<String> OPERATORS = Set.of("EQUALS", "CONTAINS", "STARTS_WITH");

    private final CategoryRepository categories;
    private final CategorizationRuleRepository rules;
    private final CategorizationService categorization;

    public CategoryAdminService(
            CategoryRepository categories,
            CategorizationRuleRepository rules,
            CategorizationService categorization) {
        this.categories = categories;
        this.rules = rules;
        this.categorization = categorization;
    }

    @Transactional(readOnly = true)
    public List<CategoryEntity> listCategories(UUID userId) {
        return categories.listVisible(userId);
    }

    @Transactional
    public CategoryEntity createCategory(UUID userId, String label, UUID parentId) {
        if (label == null || label.isBlank()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        CategoryEntity entity = new CategoryEntity();
        entity.setUserId(userId);
        entity.setLabel(label.trim());
        entity.setSystem(false);
        if (parentId != null) {
            categorization.requireVisible(userId, parentId);
            entity.setParentId(parentId);
        }
        return categories.save(entity);
    }

    @Transactional(readOnly = true)
    public List<CategorizationRuleEntity> listRules(UUID userId) {
        return rules.findByUserIdOrderByPriorityAsc(userId);
    }

    @Transactional
    public CategorizationRuleEntity createRule(
            UUID userId,
            int priority,
            String field,
            String operator,
            String matchValue,
            BigDecimal amountMin,
            BigDecimal amountMax,
            UUID targetCategoryId,
            Boolean enabled) {
        CategorizationRuleEntity entity = new CategorizationRuleEntity();
        entity.setUserId(userId);
        applyRule(
                userId,
                entity,
                priority,
                field,
                operator,
                matchValue,
                amountMin,
                amountMax,
                targetCategoryId,
                enabled == null || enabled);
        CategorizationRuleEntity saved = rules.save(entity);
        categorization.recategorizeNonManual(userId);
        return saved;
    }

    @Transactional
    public CategorizationRuleEntity updateRule(
            UUID userId,
            UUID ruleId,
            Integer priority,
            String field,
            String operator,
            String matchValue,
            BigDecimal amountMin,
            boolean amountMinPresent,
            BigDecimal amountMax,
            boolean amountMaxPresent,
            UUID targetCategoryId,
            Boolean enabled) {
        CategorizationRuleEntity entity = rules.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
        applyRule(
                userId,
                entity,
                priority == null ? entity.getPriority() : priority,
                field == null ? entity.getField() : field,
                operator == null ? entity.getOperator() : operator,
                matchValue == null ? entity.getMatchValue() : matchValue,
                amountMinPresent ? amountMin : entity.getAmountMin(),
                amountMaxPresent ? amountMax : entity.getAmountMax(),
                targetCategoryId == null ? entity.getTargetCategoryId() : targetCategoryId,
                enabled == null ? entity.isEnabled() : enabled);
        CategorizationRuleEntity saved = rules.save(entity);
        categorization.recategorizeNonManual(userId);
        return saved;
    }

    @Transactional
    public void deleteRule(UUID userId, UUID ruleId) {
        CategorizationRuleEntity entity = rules.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
        rules.delete(entity);
        categorization.recategorizeNonManual(userId);
    }

    private void applyRule(
            UUID userId,
            CategorizationRuleEntity entity,
            int priority,
            String field,
            String operator,
            String matchValue,
            BigDecimal amountMin,
            BigDecimal amountMax,
            UUID targetCategoryId,
            boolean enabled) {
        if (priority < 0 || matchValue == null || matchValue.isBlank() || matchValue.length() > 200) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        String normalizedField = field == null ? "" : field.toUpperCase(Locale.ROOT);
        String normalizedOperator = operator == null ? "" : operator.toUpperCase(Locale.ROOT);
        if (!FIELDS.contains(normalizedField) || !OPERATORS.contains(normalizedOperator)) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        if ("DIRECTION".equals(normalizedField)) {
            if (!"EQUALS".equals(normalizedOperator)) {
                throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
            }
            String direction = matchValue.trim().toUpperCase(Locale.ROOT);
            if (!"CREDIT".equals(direction) && !"DEBIT".equals(direction)) {
                throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
            }
            entity.setMatchValue(direction);
        } else {
            entity.setMatchValue(matchValue.trim());
        }
        if (amountMin != null && amountMax != null && amountMin.compareTo(amountMax) > 0) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        categorization.requireVisible(userId, targetCategoryId);
        entity.setPriority(priority);
        entity.setField(normalizedField);
        entity.setOperator(normalizedOperator);
        entity.setAmountMin(amountMin);
        entity.setAmountMax(amountMax);
        entity.setTargetCategoryId(targetCategoryId);
        entity.setEnabled(enabled);
    }
}
