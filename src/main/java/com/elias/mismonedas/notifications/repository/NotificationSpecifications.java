package com.elias.mismonedas.notifications.repository;

import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.shared.repository.TextSearchSpecification;
import org.springframework.data.jpa.domain.Specification;

public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<Notification> list(Long userId, NotificationState state, String search) {
        Specification<Notification> stateFilter = state == null
                ? (root, query, cb) -> cb.notEqual(root.get("state"), NotificationState.DISCARD)
                : (root, query, cb) -> cb.equal(root.get("state"), state);

        return Specification.allOf(
                (root, query, cb) -> cb.equal(root.get("user").get("id"), userId),
                stateFilter,
                TextSearchSpecification.containsIgnoreCase(search, "message")
        );
    }
}
