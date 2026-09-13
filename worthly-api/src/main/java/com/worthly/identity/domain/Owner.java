package com.worthly.identity.domain;

import java.util.UUID;

public record Owner(
        UUID id,
        String email,
        String reportingTimezone,
        String reportingCurrency,
        OwnerStatus status) {}
