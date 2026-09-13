package com.worthly.banking.transactions.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalTransactionRepository extends JpaRepository<ExternalTransactionEntity, UUID> {

    Optional<ExternalTransactionEntity> findByAccountIdAndProviderTransactionId(
            UUID accountId, String providerTransactionId);

    Optional<ExternalTransactionEntity> findByAccountIdAndFallbackFingerprint(
            UUID accountId, String fallbackFingerprint);

    long countByAccountId(UUID accountId);

    List<ExternalTransactionEntity> findAllByAccountId(UUID accountId);
}
