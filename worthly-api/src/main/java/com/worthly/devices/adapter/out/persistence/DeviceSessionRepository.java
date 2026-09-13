package com.worthly.devices.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceSessionRepository extends JpaRepository<DeviceSessionEntity, UUID> {

    List<DeviceSessionEntity> findByUserIdOrderByLastSeenAtDesc(UUID userId);

    Optional<DeviceSessionEntity> findByRefreshFamilyId(UUID familyId);

    Optional<DeviceSessionEntity> findFirstByUserIdAndPlatformAndRevokedAtIsNullOrderByLastSeenAtDesc(
            UUID userId, String platform);
}
