package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.notifications.template.RenderedNotification;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleNotificationListenerTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationMessageFormatter formatter;
    @Mock private UserRepository userRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private ScheduleRepository scheduleRepository;

    @Test
    void shouldCreateScheduledBudgetNotificationInTheAfterCommitTransaction() {
        User user = new User();
        Budget budget = Budget.builder().id(4L).build();
        when(userRepository.getReferenceById(8L)).thenReturn(user);
        when(budgetRepository.getReferenceById(4L)).thenReturn(budget);
        when(formatter.budgetScheduleExecuted(any(), eq(user)))
                .thenReturn(new RenderedNotification("BUDGET", "<p>BUDGET</p>"));
        ScheduleNotificationListener listener = new ScheduleNotificationListener(
                notificationService, formatter, userRepository, budgetRepository, scheduleRepository, Runnable::run);

        listener.onBudgetRegistered(new BudgetRegisteredEvent(4L, 8L));

        ArgumentCaptor<Consumer<Notification.NotificationBuilder>> builder = ArgumentCaptor.forClass(Consumer.class);
        verify(notificationService).generate(builder.capture());
        Notification.NotificationBuilder notificationBuilder = Notification.builder();
        builder.getValue().accept(notificationBuilder);
        Notification notification = notificationBuilder.build();
        assertThat(notification.getType()).isEqualTo(NotificationType.INFO);
        assertThat(notification.getUser()).isSameAs(user);
        assertThat(notification.getBudget()).isSameAs(budget);
        assertThat(notification.getMessage()).isEqualTo("BUDGET");
    }
}
