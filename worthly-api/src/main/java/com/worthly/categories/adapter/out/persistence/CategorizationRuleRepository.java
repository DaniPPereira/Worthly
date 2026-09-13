package com.worthly.categories.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRuleEntity, UUID> {

    List<CategorizationRuleEntity> findByUserIdOrderByPriorityAsc(UUID userId);

    List<CategorizationRuleEntity> findByUserIdAndEnabledIsTrueOrderByPriorityAsc(UUID userId);

    Optional<CategorizationRuleEntity> findByIdAndUserId(UUID id, UUID userId);
}
