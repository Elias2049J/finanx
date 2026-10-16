package com.elias.mismonedas.catalog.repository;

import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.shared.repository.TextSearchSpecification;
import org.springframework.data.jpa.domain.Specification;

public final class ReasonSpecifications {

    private ReasonSpecifications() {
    }

    public static Specification<Reason> list(Long userId, Boolean active, String search) {
        return Specification.allOf(
                (root, query, cb) -> cb.equal(root.get("user").get("id"), userId),
                (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active),
                TextSearchSpecification.containsIgnoreCase(search, "description")
        );
    }
}
