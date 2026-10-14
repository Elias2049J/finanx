package com.elias.mismonedas.notifications.outbox.persistence;

import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findAllByState(OutboxState state);

    long deleteByStateInAndCreatedAtBefore(Collection<OutboxState> states, OffsetDateTime cutoff);

    @Query("""
    SELECT COUNT(o) > 0
    FROM NotificationOutbox o
    JOIN Notification n ON n.id = o.notificationId
    WHERE n.budget.id = :budgetId
    AND o.state = :state
""")
    boolean existsByBudgetIdAndState(
            @Param("budgetId") Long budgetId,
            @Param("state") OutboxState state
    );
}
