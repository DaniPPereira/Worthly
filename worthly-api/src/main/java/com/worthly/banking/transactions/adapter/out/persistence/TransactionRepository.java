package com.worthly.banking.transactions.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByExternalTransactionId(UUID externalTransactionId);

    Page<TransactionEntity> findByAccountIdIn(Collection<UUID> accountIds, Pageable pageable);

    Page<TransactionEntity> findByAccountId(UUID accountId, Pageable pageable);

    List<TransactionEntity> findAllByAccountId(UUID accountId);

    long countByAccountId(UUID accountId);
}
