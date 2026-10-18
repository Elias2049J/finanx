package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;






@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationsListener {

    private final NotificationRepository notificationRepository;
    private final FcmTokenRepository fcmTokenRepository;

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        notificationRepository.deleteAllByUser_Id(event.userId());
        fcmTokenRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted notifications and FCM tokens for user {}", event.userId());
    }
}
