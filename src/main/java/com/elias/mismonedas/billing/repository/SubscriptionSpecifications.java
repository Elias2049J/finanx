package com.elias.mismonedas.billing.repository;

import com.elias.mismonedas.billing.domain.entity.Subscription;
import com.elias.mismonedas.billing.domain.enums.SubscriptionState;
import org.springframework.data.jpa.domain.Specification;

public final class SubscriptionSpecifications {

    private SubscriptionSpecifications() {
    }

    public static Specification<Subscription> list(Long userId, SubscriptionState state) {
        return Specification.allOf(
                (root, query, cb) -> cb.equal(root.get("user").get("id"), userId),
                (root, query, cb) -> state == null ? cb.conjunction() : cb.equal(root.get("state"), state)
        );
    }
}
