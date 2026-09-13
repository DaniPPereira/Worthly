package com.worthly.investments.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentAccountRepository extends JpaRepository<InvestmentAccountEntity, UUID> {

    List<InvestmentAccountEntity> findByUserId(UUID userId);

    List<InvestmentAccountEntity> findByProviderConnectionId(UUID providerConnectionId);

    Optional<InvestmentAccountEntity> findByProviderConnectionIdAndProviderAccountId(
            UUID providerConnectionId, String providerAccountId);
}
