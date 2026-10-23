package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.notifications.template.NotificationMessageFormatter;
import com.elias.mismonedas.notifications.template.RenderedNotification;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
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
class BudgetNotificationListenerTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationMessageFormatter formatter;
    @Mock private UserRepository userRepository;
    @Mock private BudgetRepository budgetRepository;

    @Captor private ArgumentCaptor<Consumer<Notification.NotificationBuilder>> builderCaptor;

    private BudgetNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new BudgetNotificationListener(
                notificationService, formatter, userRepository, budgetRepository);
    }

    @Test
    void shouldGenerateWarningNotificationOnExceeded() {
        User user = new User();
        Budget budget = Budget.builder().id(1L).build();
        when(userRepository.getReferenceById(2L)).thenReturn(user);
        when(budgetRepository.getReferenceById(1L)).thenReturn(budget);
        when(formatter.budgetExceeded(any(), eq(user))).thenReturn(new RenderedNotification("MSG", "<html/>"));

        listener.onBudgetExceeded(new BudgetExceededEvent(
                1L, 2L, "Comida", BigDecimal.ONE, BigDecimal.TEN, "PEN",
                OffsetDateTime.now(), BigDecimal.ONE, "Efectivo"));

        Notification notification = capturedNotification();
        assertThat(notification.getType()).isEqualTo(NotificationType.WARNING);
        assertThat(notification.getMessage()).isEqualTo("MSG");
        assertThat(notification.getUser()).isSameAs(user);
        assertThat(notification.getBudget()).isSameAs(budget);
    }

    @Test
    void shouldGenerateWarningNotificationOnNearLimit() {
        User user = new User();
        Budget budget = Budget.builder().id(1L).build();
        when(userRepository.getReferenceById(2L)).thenReturn(user);
        when(budgetRepository.getReferenceById(1L)).thenReturn(budget);
        when(formatter.budgetNearLimit(any(), eq(user))).thenReturn(new RenderedNotification("NEAR", "<html/>"));

        listener.onBudgetNearLimit(new BudgetNearLimitEvent(
                1L, 2L, "Comida", BigDecimal.ONE, BigDecimal.TEN, 80, "PEN",
                OffsetDateTime.now(), BigDecimal.ONE, "Efectivo"));

        Notification notification = capturedNotification();
        assertThat(notification.getType()).isEqualTo(NotificationType.WARNING);
        assertThat(notification.getMessage()).isEqualTo("NEAR");
        assertThat(notification.getBudget()).isSameAs(budget);
    }

    private Notification capturedNotification() {
        verify(notificationService).generate(builderCaptor.capture());
        Notification.NotificationBuilder builder = Notification.builder();
        builderCaptor.getValue().accept(builder);
        return builder.build();
    }
}
