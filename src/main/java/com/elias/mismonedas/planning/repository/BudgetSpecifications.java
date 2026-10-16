package com.elias.mismonedas.planning.repository;

import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import com.elias.mismonedas.shared.repository.DateRangeSpecification;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

public final class BudgetSpecifications {

    private BudgetSpecifications() {
    }

    public static Specification<Budget> list(
            Long userId,
            Boolean active,
            BudgetState state,
            BudgetHealth health,
            OffsetDateTime from,
            OffsetDateTime to) {
        Specification<Budget> dateRange = from == null
                ? (root, query, cb) -> cb.conjunction()
                : DateRangeSpecification.between("createdAt", from, to);

        return Specification.allOf(
                (root, query, cb) -> cb.equal(root.get("user").get("id"), userId),
                (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active),
                (root, query, cb) -> state == null ? cb.conjunction() : cb.equal(root.get("state"), state),
                (root, query, cb) -> health == null ? cb.conjunction() : cb.equal(root.get("health"), health),
                dateRange
        );
    }
}
