package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.domain.event.BudgetScheduleExecutedEvent;
import com.elias.mismonedas.scheduling.domain.event.TransactionScheduleExecutedEvent;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ScheduleNotificationListener {

    private final NotificationService notificationService;
    private final NotificationMessageFormatter formatter;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final BudgetRepository budgetRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTransactionScheduleExecuted(TransactionScheduleExecutedEvent event) {
        User user = userRepository.getReferenceById(event.userId());

        notificationService.generate(builder -> builder
                .user(user)
                .schedule(scheduleRepository.getReferenceById(event.scheduleId()))
                .type(NotificationType.INFO)
                .message(formatter.transactionScheduleExecuted(event)));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBudgetScheduleExecuted(BudgetScheduleExecutedEvent event) {
        User user = userRepository.getReferenceById(event.userId());

        notificationService.generate(builder -> builder
                .user(user)
                .schedule(scheduleRepository.getReferenceById(event.scheduleId()))
                .budget(budgetRepository.getReferenceById(event.budgetId()))
                .type(NotificationType.INFO)
                .message(formatter.budgetScheduleExecuted(event)));
    }
}
