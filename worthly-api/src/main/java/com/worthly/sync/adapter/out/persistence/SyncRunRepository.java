package com.worthly.sync.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRunEntity, UUID> {

    Page<SyncRunEntity> findByConnectionIdOrderByStartedAtDesc(UUID connectionId, Pageable pageable);

    boolean existsByConnectionIdAndStatus(UUID connectionId, String status);
}
