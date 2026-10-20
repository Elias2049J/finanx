package com.elias.mismonedas.notifications.service;

import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

public interface NotificationService {

    void generate(Consumer<Notification.NotificationBuilder> builderConsumer);

    NotificationDTO findById(Long id);

    PageResponse<NotificationDTO> list(NotificationState state, String search, Pageable pageable);

    void markAsRead(Long id);

    void discard(Long id);

    void purgeOlderThan(LocalDateTime threshold);
}
