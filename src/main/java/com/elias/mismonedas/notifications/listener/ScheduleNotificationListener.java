package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.port.NotificationTransactionRunner;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.notifications.template.RenderedNotification;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.event.SchedulePendingConfirmationEvent;
import com.elias.mismonedas.scheduling.repository.BudgetScheduleRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import com.elias.mismonedas.scheduling.repository.TransactionScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleNotificationListener {

    private static final String ACTION_OPEN_REMINDERS = "OPEN_REMINDERS";

    private final NotificationService notificationService;
    private final NotificationMessageFormatter formatter;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final ScheduleRepository scheduleRepository;
    private final TransactionScheduleRepository transactionScheduleRepository;
    private final BudgetScheduleRepository budgetScheduleRepository;
    private final NotificationTransactionRunner transactionRunner;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionRegistered(TransactionRegisteredEvent event) {
        if (event.scheduleId() == null) {
            return;
        }

        try {
            transactionRunner.runInNewTransaction(() -> {
                User user = userRepository.getReferenceById(event.userId());
                Schedule schedule = scheduleRepository.getReferenceById(event.scheduleId());
                RenderedNotification rendered = formatter.transactionScheduleExecuted(
                        event, user, schedule.getExecutionMode());

                notificationService.generate(builder -> builder
                        .user(user)
                        .schedule(schedule)
                        .type(NotificationType.INFO)
                        .title(rendered.title())
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
                Schedule schedule = scheduleRepository.getReferenceById(event.scheduleId());
                Budget budget = budgetRepository.getReferenceById(event.budgetId());
                RenderedNotification rendered = formatter.budgetScheduleExecuted(
                        event, user, schedule.getExecutionMode(), budget.getDescription());

                notificationService.generate(builder -> builder
                        .user(user)
                        .schedule(schedule)
                        .budget(budget)
                        .type(NotificationType.INFO)
                        .title(rendered.title())
                        .message(rendered.text())
                        .htmlMessage(rendered.html()));
            });
        } catch (RuntimeException ex) {
            log.error("scheduled_budget_notification_failed_after_commit user_id={} budget_id={}",
                    event.userId(), event.budgetId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPendingConfirmation(SchedulePendingConfirmationEvent event) {
        try {
            transactionRunner.runInNewTransaction(() -> {
                User user = userRepository.getReferenceById(event.userId());
                RenderedNotification rendered;
                Schedule schedule;
                if ("budget".equals(event.scheduleType())) {
                    BudgetSchedule budgetSchedule = budgetScheduleRepository.findById(event.scheduleId())
                            .orElseThrow();
                    schedule = budgetSchedule;
                    rendered = formatter.budgetSchedulePending(budgetSchedule, user);
                } else {
                    TransactionSchedule transactionSchedule = transactionScheduleRepository
                            .findById(event.scheduleId())
                            .orElseThrow();
                    schedule = transactionSchedule;
                    rendered = formatter.transactionSchedulePending(transactionSchedule, user);
                }

                notificationService.generate(builder -> builder
                        .user(user)
                        .schedule(schedule)
                        .type(NotificationType.INFO)
                        .title(rendered.title())
                        .action(ACTION_OPEN_REMINDERS)
                        .message(rendered.text())
                        .htmlMessage(rendered.html()));
            });
        } catch (RuntimeException ex) {
            log.error("schedule_pending_notification_failed_after_commit user_id={} schedule_id={}",
                    event.userId(), event.scheduleId(), ex);
        }
    }
}
