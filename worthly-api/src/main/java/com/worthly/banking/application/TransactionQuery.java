package com.worthly.banking.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionQuery(
        UUID accountId,
        LocalDate from,
        LocalDate to,
        UUID categoryId,
        String economicType,
        String direction,
        String lifecycleStatus,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String text) {}
