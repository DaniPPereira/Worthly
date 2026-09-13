package com.worthly.categories.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    @Query(
            """
            select c from CategoryEntity c
            where c.system = true or c.userId = :userId
            order by c.label asc
            """)
    List<CategoryEntity> listVisible(@Param("userId") UUID userId);

    Optional<CategoryEntity> findByCode(String code);

    Optional<CategoryEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<CategoryEntity> findByIdAndSystemIsTrue(UUID id);
}
