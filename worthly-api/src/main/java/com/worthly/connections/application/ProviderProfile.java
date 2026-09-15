package com.worthly.connections.application;

import java.util.List;

public record ProviderProfile(
        String provider,
        InstitutionBrand brand,
        String displayName,
        ProviderKind kind,
        ProviderAuthMode authMode,
        List<ProviderCapability> capabilities,
        boolean holdingsIncluded,
        String dataScope) {}
