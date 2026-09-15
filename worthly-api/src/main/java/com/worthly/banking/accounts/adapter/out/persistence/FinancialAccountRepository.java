package com.worthly.banking.accounts.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccountEntity, UUID> {

    List<FinancialAccountEntity> findByUserIdOrderByDisplayNameAsc(UUID userId);

    List<FinancialAccountEntity> findByConnectionId(UUID connectionId);

    boolean existsByConnectionId(UUID connectionId);

    Optional<FinancialAccountEntity> findByConnectionIdAndIdentificationHashAndActiveIsTrue(
            UUID connectionId, String identificationHash);

    Optional<FinancialAccountEntity> findByConnectionIdAndProviderAccountAliasAndActiveIsTrue(
            UUID connectionId, String providerAccountAlias);

    Optional<FinancialAccountEntity> findByIdAndUserId(UUID id, UUID userId);
}
