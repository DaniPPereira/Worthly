package com.worthly.banking.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountEntity;
import com.worthly.banking.accounts.adapter.out.persistence.FinancialAccountRepository;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity;
import com.worthly.banking.transactions.adapter.out.persistence.TransactionRepository;
import com.worthly.categories.adapter.out.persistence.CategoryEntity;
import com.worthly.categories.application.CategorizationService;
import com.worthly.shared.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionWriteService {

    private final TransactionRepository transactions;
    private final FinancialAccountRepository accounts;
    private final CategorizationService categorization;

    public TransactionWriteService(
            TransactionRepository transactions,
            FinancialAccountRepository accounts,
            CategorizationService categorization) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categorization = categorization;
    }

    @Transactional
    public TransactionEntity patch(UUID userId, UUID transactionId, JsonNode body) {
        if (body == null || body.isEmpty() || (!body.has("categoryId") && !body.has("notes"))) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        TransactionEntity transaction = transactions
                .findById(transactionId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
        FinancialAccountEntity account = accounts
                .findById(transaction.getAccountId())
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
        if (!userId.equals(account.getUserId())) {
            throw ApiException.of(HttpStatus.NOT_FOUND, "not_found");
        }
        if (body.has("categoryId")) {
            JsonNode categoryNode = body.get("categoryId");
            if (categoryNode == null || categoryNode.isNull()) {
                transaction.setCategoryId(null);
                transaction.setCategorizationSource("UNCATEGORIZED");
                categorization.applyAutomatic(userId, transaction);
            } else {
                UUID categoryId;
                try {
                    categoryId = UUID.fromString(categoryNode.asText());
                } catch (IllegalArgumentException ex) {
                    throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
                }
                CategoryEntity category = categorization.requireVisible(userId, categoryId);
                transaction.setCategoryId(category.getId());
                transaction.setCategorizationSource("MANUAL");
                transaction.setEconomicType(categorization.economicTypeFor(category));
            }
        }
        if (body.has("notes")) {
            JsonNode notes = body.get("notes");
            if (notes == null || notes.isNull()) {
                transaction.setNotes(null);
            } else {
                String text = notes.asText();
                if (text.length() > 1000) {
                    throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
                }
                transaction.setNotes(text);
            }
        }
        return transactions.save(transaction);
    }
}
