package com.elias.mismonedas.notifications.outbox.persistence;

import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
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
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from NotificationOutbox outbox
            where outbox.notificationId in (
                select notification.id from Notification notification where notification.user.id = :userId
            )
            """)
    int deleteAllForUser(@Param("userId") Long userId);

    @Query("""
            select o from NotificationOutbox o
            where o.state = :state
              and (o.claimedUntil is null or o.claimedUntil < :now)
            order by o.id asc
            """)
    List<NotificationOutbox> findClaimable(
            @Param("state") OutboxState state,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    List<NotificationOutbox> findTop50ByStateAndNextRetryAtLessThanEqualOrderByIdAsc(
            OutboxState state,
            OffsetDateTime nextRetryAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from NotificationOutbox o where o.id = :id")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update NotificationOutbox o
               set o.claimedUntil = :leaseUntil
             where o.id = :id
               and o.state = :pending
               and (o.claimedUntil is null or o.claimedUntil < :now)
            """)
    int claimIfPending(
            @Param("id") Long id,
            @Param("pending") OutboxState pending,
            @Param("now") OffsetDateTime now,
            @Param("leaseUntil") OffsetDateTime leaseUntil
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update NotificationOutbox o
               set o.state = :published, o.claimedUntil = null
             where o.id = :id
               and o.state = :pending
               and o.claimedUntil = :leaseUntil
            """)
    int markPublishedIfClaimed(
            @Param("id") Long id,
            @Param("pending") OutboxState pending,
            @Param("published") OutboxState published,
            @Param("leaseUntil") OffsetDateTime leaseUntil
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update NotificationOutbox o
               set o.claimedUntil = null
             where o.id = :id
               and o.state = :pending
               and o.claimedUntil = :leaseUntil
            """)
    int releaseClaim(
            @Param("id") Long id,
            @Param("pending") OutboxState pending,
            @Param("leaseUntil") OffsetDateTime leaseUntil
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            DELETE FROM notification_outbox
            WHERE id IN (
                SELECT id
                FROM notification_outbox
                WHERE state IN (:states)
                  AND created_at < :cutoff
                ORDER BY id
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteBatchByStateInAndCreatedAtBefore(
            @Param("states") Collection<String> states,
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("batchSize") int batchSize
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            DELETE FROM notification_outbox
            WHERE id IN (
                SELECT id
                FROM notification_outbox
                WHERE state IN ('FAILED', 'PUBLISHED')
                  AND created_at < :cutoff
                  AND (next_retry_at IS NULL OR next_retry_at <= :now)
                ORDER BY id
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteStaleBatchBefore(
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("now") OffsetDateTime now,
            @Param("batchSize") int batchSize
    );

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
