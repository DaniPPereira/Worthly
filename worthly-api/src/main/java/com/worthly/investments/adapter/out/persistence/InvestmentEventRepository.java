package com.worthly.investments.adapter.out.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface InvestmentEventRepository extends JpaRepository<InvestmentEventEntity, UUID> {

    Optional<InvestmentEventEntity> findByInvestmentAccountIdAndProviderEventId(
            UUID investmentAccountId, String providerEventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteByInvestmentAccountIdIn(Collection<UUID> accountIds);
}
