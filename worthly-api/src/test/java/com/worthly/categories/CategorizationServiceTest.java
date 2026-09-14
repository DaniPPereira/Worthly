package com.worthly.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.categories.adapter.out.persistence.CategorizationRuleEntity;
import com.worthly.categories.adapter.out.persistence.CategorizationRuleRepository;
import com.worthly.categories.adapter.out.persistence.CategoryEntity;
import com.worthly.categories.adapter.out.persistence.CategoryRepository;
import com.worthly.categories.application.CategorizationService;
import com.worthly.categories.application.HeuristicCatalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategorizationServiceTest {

    @Mock
    CategoryRepository categories;

    @Mock
    CategorizationRuleRepository rules;

    @Mock
    FinancialAccountRepository accounts;

    @Mock
    TransactionRepository transactions;

    HeuristicCatalog heuristics;
    CategorizationService service;
    UUID userId = UUID.randomUUID();

    CategoryEntity subscriptions;
    CategoryEntity groceries;
    CategoryEntity uncategorized;

    @BeforeEach
    void setUp() throws Exception {
        heuristics = new HeuristicCatalog(new ObjectMapper());
        heuristics.load();
        service = new CategorizationService(categories, rules, heuristics, accounts, transactions);
        subscriptions = category("expense.subscriptions");
        groceries = category("expense.groceries");
        uncategorized = category("uncategorized");
    }

    @Test
    void heuristicAssignsNetflixBeforeUncategorized() {
        when(rules.findByUserIdAndEnabledIsTrueOrderByPriorityAsc(userId)).thenReturn(List.of());
        when(categories.findByCode("expense.subscriptions")).thenReturn(Optional.of(subscriptions));
        TransactionEntity tx = transaction("Netflix", "Subscription");
        service.applyAutomatic(userId, tx);
        assertThat(tx.getCategoryId()).isEqualTo(subscriptions.getId());
        assertThat(tx.getCategorizationSource()).isEqualTo("HEURISTIC");
        assertThat(tx.getEconomicType()).isEqualTo("EXPENSE");
    }

    @Test
    void enabledRuleBeatsHeuristic() {
        CategorizationRuleEntity rule = new CategorizationRuleEntity();
        rule.setUserId(userId);
        rule.setPriority(0);
        rule.setField("MERCHANT");
        rule.setOperator("CONTAINS");
        rule.setMatchValue("netflix");
        rule.setTargetCategoryId(groceries.getId());
        rule.setEnabled(true);
        when(rules.findByUserIdAndEnabledIsTrueOrderByPriorityAsc(userId)).thenReturn(List.of(rule));
        when(categories.findById(groceries.getId())).thenReturn(Optional.of(groceries));
        TransactionEntity tx = transaction("Netflix", "ignored");
        service.applyAutomatic(userId, tx);
        assertThat(tx.getCategoryId()).isEqualTo(groceries.getId());
        assertThat(tx.getCategorizationSource()).isEqualTo("RULE");
    }

    @Test
    void manualIsNotOverwritten() {
        TransactionEntity tx = transaction("Netflix", "Subscription");
        tx.setCategorizationSource("MANUAL");
        tx.setCategoryId(groceries.getId());
        tx.setEconomicType("EXPENSE");
        service.applyAutomatic(userId, tx);
        assertThat(tx.getCategoryId()).isEqualTo(groceries.getId());
        assertThat(tx.getCategorizationSource()).isEqualTo("MANUAL");
    }

    @Test
    void shortContainsKeysAreIgnored() {
        when(rules.findByUserIdAndEnabledIsTrueOrderByPriorityAsc(userId)).thenReturn(List.of());
        when(categories.findByCode("uncategorized")).thenReturn(Optional.of(uncategorized));
        TransactionEntity tx = transaction("Cafe Test", "Coffee");
        service.applyAutomatic(userId, tx);
        assertThat(tx.getCategoryId()).isEqualTo(uncategorized.getId());
        assertThat(tx.getCategorizationSource()).isEqualTo("UNCATEGORIZED");
        assertThat(tx.getEconomicType()).isEqualTo("EXPENSE");
    }

    @Test
    void uncategorizedCreditDefaultsToIncome() {
        when(rules.findByUserIdAndEnabledIsTrueOrderByPriorityAsc(userId)).thenReturn(List.of());
        when(categories.findByCode("uncategorized")).thenReturn(Optional.of(uncategorized));
        TransactionEntity tx = transaction("Employer Test", "Payroll");
        tx.setDirection("CREDIT");
        service.applyAutomatic(userId, tx);
        assertThat(tx.getEconomicType()).isEqualTo("INCOME");
        assertThat(tx.getCategorizationSource()).isEqualTo("UNCATEGORIZED");
    }

    private static CategoryEntity category(String code) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(UUID.randomUUID());
        entity.setLabel(code);
        entity.setCode(code);
        entity.setSystem(true);
        return entity;
    }

    private static TransactionEntity transaction(String merchant, String description) {
        TransactionEntity tx = new TransactionEntity();
        tx.setAccountId(UUID.randomUUID());
        tx.setExternalTransactionId(UUID.randomUUID());
        tx.setDirection("DEBIT");
        tx.setLifecycleStatus("BOOKED");
        tx.setEconomicType("OTHER");
        tx.setAmount(new BigDecimal("9.99"));
        tx.setCurrency("EUR");
        tx.setMerchant(merchant);
        tx.setDescription(description);
        tx.setReportingAt(Instant.parse("2026-01-15T00:00:00Z"));
        tx.setCategorizationSource("UNCATEGORIZED");
        return tx;
    }
}
