package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.notifications.template.RenderedNotification;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BudgetNotificationListener {

    private final NotificationService notificationService;
    private final NotificationMessageFormatter formatter;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBudgetExceeded(BudgetExceededEvent event) {
        User user = userRepository.getReferenceById(event.userId());
        RenderedNotification rendered = formatter.budgetExceeded(event, user);

        notificationService.generate(builder -> builder
                .budget(budgetRepository.getReferenceById(event.budgetId()))
                .user(user)
                .type(NotificationType.WARNING)
                .message(rendered.text())
                .htmlMessage(rendered.html()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBudgetNearLimit(BudgetNearLimitEvent event) {
        User user = userRepository.getReferenceById(event.userId());
        RenderedNotification rendered = formatter.budgetNearLimit(event, user);

        notificationService.generate(builder -> builder
                .budget(budgetRepository.getReferenceById(event.budgetId()))
                .user(user)
                .type(NotificationType.WARNING)
                .message(rendered.text())
                .htmlMessage(rendered.html()));
    }
}
