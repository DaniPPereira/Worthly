package com.worthly.banking.application;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class TransactionSpecifications {

    private TransactionSpecifications() {}

    public static Specification<com.worthly.banking.transactions.adapter.out.persistence.TransactionEntity> filter(
            Collection<java.util.UUID> accountIds, TransactionQuery query, ZoneId zone) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("accountId").in(accountIds));
            if (query.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), query.categoryId()));
            }
            if (query.economicType() != null && !query.economicType().isBlank()) {
                predicates.add(cb.equal(root.get("economicType"), query.economicType()));
            }
            if (query.direction() != null && !query.direction().isBlank()) {
                predicates.add(cb.equal(root.get("direction"), query.direction()));
            }
            if (query.lifecycleStatus() != null && !query.lifecycleStatus().isBlank()) {
                predicates.add(cb.equal(root.get("lifecycleStatus"), query.lifecycleStatus()));
            }
            if (query.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), query.minAmount()));
            }
            if (query.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), query.maxAmount()));
            }
            if (query.from() != null) {
                Instant from = query.from().atStartOfDay(zone).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("reportingAt"), from));
            }
            if (query.to() != null) {
                Instant to = query.to().plusDays(1).atStartOfDay(zone).toInstant();
                predicates.add(cb.lessThan(root.get("reportingAt"), to));
            }
            if (query.text() != null && !query.text().isBlank()) {
                String like = "%" + query.text().trim().toLowerCase(Locale.ROOT) + "%";
                var blank = cb.literal("");
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("merchant"), blank)), like),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), blank)), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
