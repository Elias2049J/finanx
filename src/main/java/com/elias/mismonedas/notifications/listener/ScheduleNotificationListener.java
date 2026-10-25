package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.port.NotificationTransactionRunner;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.notifications.template.RenderedNotification;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleNotificationListener {

    private final NotificationService notificationService;
    private final NotificationMessageFormatter formatter;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final ScheduleRepository scheduleRepository;
    private final NotificationTransactionRunner transactionRunner;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionRegistered(TransactionRegisteredEvent event) {
        if (event.scheduleId() == null) {
            return;
        }

        try {
            transactionRunner.runInNewTransaction(() -> {
                User user = userRepository.getReferenceById(event.userId());
                RenderedNotification rendered = formatter.transactionScheduleExecuted(event, user);

                notificationService.generate(builder -> builder
                        .user(user)
                        .schedule(scheduleRepository.getReferenceById(event.scheduleId()))
                        .type(NotificationType.INFO)
                        .message(rendered.text())
                        .htmlMessage(rendered.html()));
            });
        } catch (RuntimeException ex) {
            log.error("scheduled_transaction_notification_failed_after_commit user_id={} schedule_id={}",
                    event.userId(), event.scheduleId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBudgetRegistered(BudgetRegisteredEvent event) {
        try {
            transactionRunner.runInNewTransaction(() -> {
                User user = userRepository.getReferenceById(event.userId());
                RenderedNotification rendered = formatter.budgetScheduleExecuted(event, user);

                notificationService.generate(builder -> builder
                        .user(user)
                        .budget(budgetRepository.getReferenceById(event.budgetId()))
                        .type(NotificationType.INFO)
                        .message(rendered.text())
                        .htmlMessage(rendered.html()));
            });
        } catch (RuntimeException ex) {
            log.error("scheduled_budget_notification_failed_after_commit user_id={} budget_id={}",
                    event.userId(), event.budgetId(), ex);
        }
    }
}
