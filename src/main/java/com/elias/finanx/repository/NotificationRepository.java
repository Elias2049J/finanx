package com.elias.finanx.repository;

import com.elias.finanx.entity.Notification;
import com.elias.finanx.entity.enums.NotificationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUser_IdAndStateAndScheduledAtBefore(Long userId, NotificationState state, OffsetDateTime scheduledAtBefore);
    List<Notification> findAllByUser_IdAndState(Long userId, NotificationState state);
    List<Notification> findAllByUser_Id(Long userId);
    List<Notification> findAllByUser_IdAndStateAndSentAtBefore(Long userId, NotificationState stateSent, OffsetDateTime sentAtBefore);

    List<Notification> findAllByUser_IdAndStateIsNot(Long userId, NotificationState state);
}

