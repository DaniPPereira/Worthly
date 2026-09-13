package com.worthly.connections.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderConnectionRepository extends JpaRepository<ProviderConnectionEntity, UUID> {

    List<ProviderConnectionEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<ProviderConnectionEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<ProviderConnectionEntity> findFirstByUserIdAndProviderAndAspspNameAndAspspCountry(
            UUID userId, String provider, String aspspName, String aspspCountry);
}
