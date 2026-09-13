package com.worthly.investments.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PositionSnapshotRepository extends JpaRepository<PositionSnapshotEntity, UUID> {

    List<PositionSnapshotEntity> findByInvestmentAccountIdAndObservedAt(UUID investmentAccountId, Instant observedAt);

    @Query(
            """
            select p from PositionSnapshotEntity p
            where p.investmentAccountId in :accountIds
              and p.observedAt = (
                select max(p2.observedAt) from PositionSnapshotEntity p2
                where p2.investmentAccountId = p.investmentAccountId
              )
            """)
    List<PositionSnapshotEntity> findLatestByInvestmentAccountIdIn(@Param("accountIds") Collection<UUID> accountIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteByInvestmentAccountIdIn(Collection<UUID> accountIds);
}
