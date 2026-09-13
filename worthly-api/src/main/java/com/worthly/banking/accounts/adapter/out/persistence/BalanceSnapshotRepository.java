package com.worthly.banking.accounts.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshotEntity, UUID> {

    Optional<BalanceSnapshotEntity> findByAccountIdAndSourceHash(UUID accountId, String sourceHash);

    List<BalanceSnapshotEntity> findByAccountIdOrderByObservedAtDesc(UUID accountId);

    List<BalanceSnapshotEntity> findAllByAccountId(UUID accountId);
}
