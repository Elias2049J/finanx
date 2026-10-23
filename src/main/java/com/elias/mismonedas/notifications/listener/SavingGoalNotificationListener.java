package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.notifications.template.RenderedNotification;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SavingGoalNotificationListener {

    private final NotificationService notificationService;
    private final NotificationMessageFormatter formatter;
    private final UserRepository userRepository;
    private final SavingGoalRepository savingGoalRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onSavingGoalCompleted(SavingGoalCompletedEvent event) {
        User user = userRepository.getReferenceById(event.userId());
        RenderedNotification rendered = formatter.savingGoalCompleted(event, user);

        notificationService.generate(builder -> builder
                .savingGoal(savingGoalRepository.getReferenceById(event.savingGoalId()))
                .user(user)
                .type(NotificationType.SUCCESS)
                .message(rendered.text())
                .htmlMessage(rendered.html()));
    }
}
