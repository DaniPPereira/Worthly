package com.worthly.connections.application;

import java.util.List;

public record CatalogEntry(
        String id,
        String provider,
        InstitutionBrand brand,
        String name,
        String country,
        ProviderKind kind,
        ProviderAuthMode authMode,
        boolean connectable,
        boolean holdingsIncluded,
        List<ProviderCapability> capabilities,
        String dataScope,
        String unavailableReason,
        String logoUrl) {}
