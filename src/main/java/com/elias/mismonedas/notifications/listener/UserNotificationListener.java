package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import com.elias.mismonedas.identity.domain.event.UserLoggedOutEvent;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserNotificationListener {

    private final NotificationRepository notificationRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;

    @EventListener
    @Order(UserDeletionOrder.NOTIFICATIONS)
    public void onUserDeleted(UserDeletedEvent event) {
        notificationOutboxRepository.deleteAllForUser(event.userId());
        notificationRepository.deleteAllByUser_Id(event.userId());
        fcmTokenRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted notifications and FCM tokens for user {}", event.userId());
    }

    @EventListener
    @Transactional
    public void onUserLoggedOut(UserLoggedOutEvent event) {
        if (event.userId() == null || event.fcmToken() == null || event.fcmToken().isBlank()) {
            return;
        }
        int deleted = fcmTokenRepository.deleteByTokenAndUserId(event.fcmToken(), event.userId());
        log.info("FCM token removed on logout userId={} deleted={}", event.userId(), deleted);
    }
}
