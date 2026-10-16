package com.elias.mismonedas.ledger.repository;

import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.repository.DateRangeSpecification;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> list(
            Long userId,
            Boolean active,
            TransactionType type,
            Long walletId,
            Long categoryId,
            OffsetDateTime from,
            OffsetDateTime to) {
        Specification<Transaction> dateRange = from == null
                ? (root, query, cb) -> cb.conjunction()
                : DateRangeSpecification.between("createdAt", from, to);

        return Specification.allOf(
                (root, query, cb) -> cb.equal(root.get("wallet").get("user").get("id"), userId),
                (root, query, cb) -> active == null ? cb.conjunction() : cb.equal(root.get("active"), active),
                (root, query, cb) -> type == null ? cb.conjunction() : cb.equal(root.get("type"), type),
                (root, query, cb) -> walletId == null ? cb.conjunction() : cb.equal(root.get("wallet").get("id"), walletId),
                (root, query, cb) -> categoryId == null ? cb.conjunction() : cb.equal(root.get("category").get("id"), categoryId),
                dateRange
        );
    }
}
