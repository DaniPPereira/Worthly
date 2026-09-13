package com.worthly.connections.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationAttemptRepository extends JpaRepository<AuthorizationAttemptEntity, UUID> {

    Optional<AuthorizationAttemptEntity> findByStateHash(String stateHash);
}
