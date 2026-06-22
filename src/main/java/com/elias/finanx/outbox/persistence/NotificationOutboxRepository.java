package com.elias.finanx.outbox.persistence;

import com.elias.finanx.outbox.NotificationOutbox;
import com.elias.finanx.outbox.OutboxState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findAllByState(OutboxState state);

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
