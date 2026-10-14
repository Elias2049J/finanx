package com.elias.mismonedas.notifications.outbox.persistence;

import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findTop50ByStateOrderByIdAsc(OutboxState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from NotificationOutbox o where o.id = :id")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update NotificationOutbox o set o.state = :published where o.id = :id and o.state = :pending")
    int markPublishedIfPending(
            @Param("id") Long id,
            @Param("pending") OutboxState pending,
            @Param("published") OutboxState published
    );

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
