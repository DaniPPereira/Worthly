package com.worthly.identity.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCodeEntity, UUID> {

    List<TotpRecoveryCodeEntity> findByUserIdAndUsedAtIsNull(UUID userId);

    @Modifying(clearAutomatically = true)
    void deleteByUserId(UUID userId);
}
