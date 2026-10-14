package com.elias.mismonedas.notifications.service;

import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

public interface NotificationService {

    void generate(Consumer<Notification.NotificationBuilder> builderConsumer);

    NotificationDTO findById(Long id);

    List<NotificationDTO> findAllByUser(Long userId);

    List<NotificationDTO> findAllByUserAndState(Long userId, NotificationState state);

    void markAsRead(Long id);

    void discard(Long id);

    void purgeOlderThan(LocalDateTime threshold, long userId);

    List<NotificationDTO> findAllScheduledByUser(long userId);
}
