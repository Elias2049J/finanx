package com.elias.mismonedas.billing.repository;

import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.shared.repository.DateRangeSpecification;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    public static Specification<Payment> list(
            Long userId,
            PaymentState state,
            Long subscriptionId,
            OffsetDateTime from,
            OffsetDateTime to) {
        Specification<Payment> dateRange = from == null
                ? (root, query, cb) -> cb.conjunction()
                : DateRangeSpecification.between("createdAt", from, to);

        return Specification.allOf(
                (root, query, cb) -> cb.equal(root.get("user").get("id"), userId),
                (root, query, cb) -> state == null ? cb.conjunction() : cb.equal(root.get("state"), state),
                (root, query, cb) -> subscriptionId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("subscription").get("id"), subscriptionId),
                dateRange
        );
    }
}
