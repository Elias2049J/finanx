package com.elias.finanx.service;

import com.elias.finanx.dto.notification.NotificationDTO;
import com.elias.finanx.entity.Notification;
import com.elias.finanx.entity.enums.NotificationState;

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
