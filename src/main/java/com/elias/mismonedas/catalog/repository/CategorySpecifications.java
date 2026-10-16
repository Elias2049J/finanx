package com.elias.mismonedas.catalog.repository;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.repository.TextSearchSpecification;
import org.springframework.data.jpa.domain.Specification;

public final class CategorySpecifications {

    private CategorySpecifications() {
    }

    public static Specification<Category> list(
            Long userId,
            Boolean active,
            TransactionType type,
            String search) {
        return Specification.allOf(
                (root, query, cb) -> cb.equal(root.get("user").get("id"), userId),
                (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active),
                (root, query, cb) -> type == null ? cb.conjunction() : cb.equal(root.get("type"), type),
                TextSearchSpecification.containsIgnoreCase(search, "name", "description")
        );
    }
}
