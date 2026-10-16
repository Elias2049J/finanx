package com.elias.mismonedas.shared.repository;

import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

public final class DateRangeSpecification {

    private DateRangeSpecification() {
    }

    public static <T> Specification<T> between(
            String property,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.greaterThanOrEqualTo(root.<OffsetDateTime>get(property), startInclusive),
                criteriaBuilder.lessThan(root.<OffsetDateTime>get(property), endExclusive)
        );
    }
}
