package com.worthly.banking.transactions.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalTransactionRepository extends JpaRepository<ExternalTransactionEntity, UUID> {

    Optional<ExternalTransactionEntity> findByAccountIdAndProviderTransactionId(
            UUID accountId, String providerTransactionId);

    Optional<ExternalTransactionEntity> findByAccountIdAndFallbackFingerprint(
            UUID accountId, String fallbackFingerprint);

    long countByAccountId(UUID accountId);

    List<ExternalTransactionEntity> findAllByAccountId(UUID accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update ExternalTransactionEntity e
            set e.rawPayloadEncrypted = null, e.rawKeyVersion = null
            where e.rawExpiresAt <= :now and e.rawPayloadEncrypted is not null
            """)
    int expireRawPayloads(@Param("now") Instant now);
}
