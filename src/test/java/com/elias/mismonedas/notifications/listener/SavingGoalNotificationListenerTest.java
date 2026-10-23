package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.notifications.template.RenderedNotification;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingGoalNotificationListenerTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationMessageFormatter formatter;
    @Mock private UserRepository userRepository;
    @Mock private SavingGoalRepository savingGoalRepository;

    @Captor private ArgumentCaptor<Consumer<Notification.NotificationBuilder>> builderCaptor;

    private SavingGoalNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new SavingGoalNotificationListener(
                notificationService, formatter, userRepository, savingGoalRepository);
    }

    @Test
    void shouldGenerateSuccessNotificationOnCompleted() {
        User user = new User();
        SavingGoal goal = new SavingGoal();
        goal.setId(5L);
        when(userRepository.getReferenceById(2L)).thenReturn(user);
        when(savingGoalRepository.getReferenceById(5L)).thenReturn(goal);
        when(formatter.savingGoalCompleted(any(), eq(user))).thenReturn(new RenderedNotification("DONE", "<html/>"));

        listener.onSavingGoalCompleted(new SavingGoalCompletedEvent(
                5L, 2L, "Viaje", BigDecimal.ONE, BigDecimal.TEN, "PEN",
                OffsetDateTime.now(), BigDecimal.ONE));

        verify(notificationService).generate(builderCaptor.capture());
        Notification.NotificationBuilder builder = Notification.builder();
        builderCaptor.getValue().accept(builder);
        Notification notification = builder.build();

        assertThat(notification.getType()).isEqualTo(NotificationType.SUCCESS);
        assertThat(notification.getMessage()).isEqualTo("DONE");
        assertThat(notification.getUser()).isSameAs(user);
        assertThat(notification.getSavingGoal()).isSameAs(goal);
    }
}
