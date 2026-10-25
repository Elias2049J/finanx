package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.port.NotificationTransactionRunner;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.notifications.template.RenderedNotification;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetNotificationListener {

    private final NotificationService notificationService;
    private final NotificationMessageFormatter formatter;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final NotificationTransactionRunner transactionRunner;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetExceeded(BudgetExceededEvent event) {
        try {
            transactionRunner.runInNewTransaction(() -> {
                User user = userRepository.getReferenceById(event.userId());
                RenderedNotification rendered = formatter.budgetExceeded(event, user);

                notificationService.generate(builder -> builder
                        .budget(budgetRepository.getReferenceById(event.budgetId()))
                        .user(user)
                        .type(NotificationType.WARNING)
                        .message(rendered.text())
                        .htmlMessage(rendered.html()));
            });
        } catch (RuntimeException ex) {
            log.error("budget_exceeded_notification_failed_after_commit user_id={} budget_id={}",
                    event.userId(), event.budgetId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetNearLimit(BudgetNearLimitEvent event) {
        try {
            transactionRunner.runInNewTransaction(() -> {
                User user = userRepository.getReferenceById(event.userId());
                RenderedNotification rendered = formatter.budgetNearLimit(event, user);

                notificationService.generate(builder -> builder
                        .budget(budgetRepository.getReferenceById(event.budgetId()))
                        .user(user)
                        .type(NotificationType.WARNING)
                        .message(rendered.text())
                        .htmlMessage(rendered.html()));
            });
        } catch (RuntimeException ex) {
            log.error("budget_near_limit_notification_failed_after_commit user_id={} budget_id={}",
                    event.userId(), event.budgetId(), ex);
        }
    }
}
