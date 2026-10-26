package com.elias.mismonedas.notifications.repository;

import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

    List<Notification> findAllByUser_IdAndStateAndScheduledAtBefore(Long userId, NotificationState state, OffsetDateTime scheduledAtBefore);
    List<Notification> findAllByUser_IdAndState(Long userId, NotificationState state);
    List<Notification> findAllByUser_Id(Long userId);
    List<Notification> findAllByUser_IdAndStateAndSentAtBefore(Long userId, NotificationState stateSent, OffsetDateTime sentAtBefore);

    List<Notification> findAllByUser_IdAndStateIsNotOrderByCreatedAtDesc(Long userId, NotificationState state);
    boolean existsByBudget_IdAndStateInAndType(
            Long budgetId, List<NotificationState> states, NotificationType type);

    long deleteByStateInAndCreatedAtBefore(Collection<NotificationState> states, OffsetDateTime cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.state = :read
             where n.id = :id
               and n.user.id = :userId
               and n.state <> :discard
            """)
    int markReadIfNotDiscarded(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("read") NotificationState read,
            @Param("discard") NotificationState discard);

    void deleteAllByUser_Id(Long userId);
}

