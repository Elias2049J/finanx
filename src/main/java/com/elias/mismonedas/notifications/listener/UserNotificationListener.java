package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserNotificationListener {

    private final NotificationRepository notificationRepository;
    private final FcmTokenRepository fcmTokenRepository;

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        notificationRepository.deleteAllByUser_Id(event.userId());
        fcmTokenRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted notifications and FCM tokens for user {}", event.userId());
    }
}
