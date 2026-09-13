package com.worthly.transfers.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransferMatchRepository extends JpaRepository<TransferMatchEntity, UUID> {

    List<TransferMatchEntity> findByUserIdOrderByConfidenceDesc(UUID userId);

    List<TransferMatchEntity> findByUserIdAndStatus(UUID userId, String status);

    Optional<TransferMatchEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<TransferMatchEntity> findByPairFingerprintAndStatusIn(String pairFingerprint, Collection<String> statuses);

    boolean existsByPairFingerprintAndStatus(String pairFingerprint, String status);

    List<TransferMatchEntity> findByLeftTransactionIdInOrRightTransactionIdIn(
            Collection<UUID> leftIds, Collection<UUID> rightIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from TransferMatchEntity m where m.leftTransactionId in :ids or m.rightTransactionId in :ids")
    void deleteByTransactionIds(@Param("ids") Collection<UUID> ids);
}
