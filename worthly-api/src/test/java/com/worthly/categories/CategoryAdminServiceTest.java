package com.worthly.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.categories.adapter.out.persistence.CategorizationRuleEntity;
import com.worthly.categories.adapter.out.persistence.CategorizationRuleRepository;
import com.worthly.categories.adapter.out.persistence.CategoryEntity;
import com.worthly.categories.adapter.out.persistence.CategoryRepository;
import com.worthly.categories.application.CategorizationService;
import com.worthly.categories.application.CategoryAdminService;
import com.worthly.shared.web.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryAdminServiceTest {

    @Mock
    CategoryRepository categories;

    @Mock
    CategorizationRuleRepository rules;

    @Mock
    CategorizationService categorization;

    @Mock
    TransactionRepository transactions;

    CategoryAdminService admin;
    UUID userId = UUID.randomUUID();
    CategoryEntity pets;
    CategoryEntity otherExpense;
    CategoryEntity uncategorized;

    @BeforeEach
    void setUp() {
        admin = new CategoryAdminService(categories, rules, categorization, transactions);
        otherExpense = system("expense.other");
        uncategorized = system("uncategorized");
        pets = custom("Pets", otherExpense.getId());
    }

    @Test
    void updateRenamesOwnedCustomCategory() {
        when(categories.findByIdAndUserId(pets.getId(), userId)).thenReturn(Optional.of(pets));
        when(categories.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CategoryEntity updated = admin.updateCategory(userId, pets.getId(), "Vet", null);
        assertThat(updated.getLabel()).isEqualTo("Vet");
        assertThat(updated.getParentId()).isEqualTo(otherExpense.getId());
    }

    @Test
    void deleteMovesTransactionsAndRemovesRules() {
        CategorizationRuleEntity rule = new CategorizationRuleEntity();
        rule.setTargetCategoryId(pets.getId());
        TransactionEntity tx = new TransactionEntity();
        tx.setCategoryId(pets.getId());
        tx.setCategorizationSource("MANUAL");
        when(categories.findByIdAndUserId(pets.getId(), userId)).thenReturn(Optional.of(pets));
        when(rules.findByTargetCategoryId(pets.getId())).thenReturn(List.of(rule));
        when(categories.findByParentId(pets.getId())).thenReturn(List.of());
        when(categories.findByCode("uncategorized")).thenReturn(Optional.of(uncategorized));
        when(transactions.findByCategoryId(pets.getId())).thenReturn(List.of(tx));

        admin.deleteCategory(userId, pets.getId());

        assertThat(tx.getCategoryId()).isEqualTo(uncategorized.getId());
        assertThat(tx.getCategorizationSource()).isEqualTo("UNCATEGORIZED");
        verify(rules).deleteAll(List.of(rule));
        verify(categories).delete(pets);
        verify(categorization).recategorizeNonManual(userId);
    }

    @Test
    void deleteRejectsUnknownCategory() {
        UUID missing = UUID.randomUUID();
        when(categories.findByIdAndUserId(missing, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> admin.deleteCategory(userId, missing)).isInstanceOf(ApiException.class);
        verify(categories, never()).delete(any());
    }

    private CategoryEntity system(String code) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode(code);
        entity.setLabel(code);
        entity.setSystem(true);
        return entity;
    }

    private CategoryEntity custom(String label, UUID parentId) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setLabel(label);
        entity.setParentId(parentId);
        entity.setSystem(false);
        return entity;
    }
}
