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
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.ExecutionMode;
import com.elias.mismonedas.scheduling.domain.event.SchedulePendingConfirmationEvent;
import com.elias.mismonedas.scheduling.repository.BudgetScheduleRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import com.elias.mismonedas.scheduling.repository.TransactionScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
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
    @Mock private TransactionScheduleRepository transactionScheduleRepository;
    @Mock private BudgetScheduleRepository budgetScheduleRepository;

    private ScheduleNotificationListener listener() {
        return new ScheduleNotificationListener(
                notificationService, formatter, userRepository, budgetRepository,
                scheduleRepository, transactionScheduleRepository, budgetScheduleRepository,
                Runnable::run);
    }

    private Notification captureNotification() {
        ArgumentCaptor<Consumer<Notification.NotificationBuilder>> builder =
                ArgumentCaptor.forClass(Consumer.class);
        verify(notificationService).generate(builder.capture());
        Notification.NotificationBuilder notificationBuilder = Notification.builder();
        builder.getValue().accept(notificationBuilder);
        return notificationBuilder.build();
    }

    @Test
    void shouldCreateScheduledBudgetNotificationInTheAfterCommitTransaction() {
        User user = new User();
        Budget budget = Budget.builder().id(4L).description("Transporte").build();
        Schedule schedule = new TransactionSchedule();
        schedule.setExecutionMode(ExecutionMode.MANUAL);
        when(userRepository.getReferenceById(8L)).thenReturn(user);
        when(budgetRepository.getReferenceById(4L)).thenReturn(budget);
        when(scheduleRepository.getReferenceById(3L)).thenReturn(schedule);
        when(formatter.budgetScheduleExecuted(any(), eq(user), eq(ExecutionMode.MANUAL), eq("Transporte")))
                .thenReturn(new RenderedNotification("Presupuesto registrado", "BUDGET", "<p>BUDGET</p>"));

        listener().onBudgetRegistered(new BudgetRegisteredEvent(4L, 8L, 3L));

        Notification notification = captureNotification();
        assertThat(notification.getType()).isEqualTo(NotificationType.INFO);
        assertThat(notification.getUser()).isSameAs(user);
        assertThat(notification.getBudget()).isSameAs(budget);
        assertThat(notification.getSchedule()).isSameAs(schedule);
        assertThat(notification.getTitle()).isEqualTo("Presupuesto registrado");
        assertThat(notification.getAction()).isNull();
        assertThat(notification.getMessage()).isEqualTo("BUDGET");
    }

    @Test
    void shouldCreateActionableReminderWhenTransactionOccurrenceBecomesPending() {
        User user = new User();
        TransactionSchedule schedule = new TransactionSchedule();
        when(userRepository.getReferenceById(8L)).thenReturn(user);
        when(transactionScheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));
        when(formatter.transactionSchedulePending(schedule, user))
                .thenReturn(new RenderedNotification("Recordatorio", "REMINDER", "<p>REMINDER</p>"));

        listener().onPendingConfirmation(new SchedulePendingConfirmationEvent(5L, 8L, "transaction"));

        Notification notification = captureNotification();
        assertThat(notification.getType()).isEqualTo(NotificationType.INFO);
        assertThat(notification.getUser()).isSameAs(user);
        assertThat(notification.getSchedule()).isSameAs(schedule);
        assertThat(notification.getTitle()).isEqualTo("Recordatorio");
        assertThat(notification.getAction()).isEqualTo("OPEN_REMINDERS");
        assertThat(notification.getMessage()).isEqualTo("REMINDER");
    }

    @Test
    void shouldCreateActionableReminderWhenBudgetOccurrenceBecomesPending() {
        User user = new User();
        BudgetSchedule schedule = new BudgetSchedule();
        when(userRepository.getReferenceById(8L)).thenReturn(user);
        when(budgetScheduleRepository.findById(6L)).thenReturn(Optional.of(schedule));
        when(formatter.budgetSchedulePending(schedule, user))
                .thenReturn(new RenderedNotification("Recordatorio", "BUDGET_REMINDER", "<p>BUDGET</p>"));

        listener().onPendingConfirmation(new SchedulePendingConfirmationEvent(6L, 8L, "budget"));

        Notification notification = captureNotification();
        assertThat(notification.getSchedule()).isSameAs(schedule);
        assertThat(notification.getAction()).isEqualTo("OPEN_REMINDERS");
        assertThat(notification.getMessage()).isEqualTo("BUDGET_REMINDER");
    }
}
