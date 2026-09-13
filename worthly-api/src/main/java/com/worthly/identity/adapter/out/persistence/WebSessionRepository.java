package com.worthly.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebSessionRepository extends JpaRepository<WebSessionEntity, UUID> {

    Optional<WebSessionEntity> findByTokenHash(String tokenHash);

    List<WebSessionEntity> findByUserIdAndRevokedAtIsNull(UUID userId);

    @Modifying
    @Query("update WebSessionEntity s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
