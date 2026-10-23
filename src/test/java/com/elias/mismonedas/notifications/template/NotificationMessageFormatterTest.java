package com.elias.mismonedas.notifications.template;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMessageFormatterTest {

    @Mock private DateMapper dateMapper;
    @Mock private SpringTemplateEngine templateEngine;

    private NotificationMessageFormatter formatter;
    private final User user = new User();
    private final OffsetDateTime when = OffsetDateTime.parse("2024-01-15T10:30:00-05:00");

    @BeforeEach
    void setUp() {
        formatter = new NotificationMessageFormatter(dateMapper, templateEngine);
        when(dateMapper.toStringES(any(OffsetDateTime.class), any(ZoneId.class))).thenReturn("FECHA");
    }

    @Test
    void budgetExceededRendersTextAndHtml() {
        BudgetExceededEvent event = new BudgetExceededEvent(
                1L, 2L, "Comida",
                new BigDecimal("120.00"), new BigDecimal("100.00"), "PEN",
                when, new BigDecimal("25.50"), "Efectivo");

        RenderedNotification rendered = formatter.budgetExceeded(event, user);

        assertThat(rendered.text()).isEqualTo(
                "Has superado el límite de tu presupuesto para: Comida. Gastado: 120.00 PEN de 100.00 PEN. Último movimiento: 25.50 PEN (Efectivo).");
        verify(templateEngine).process(eq("notifications/budget-exceeded"), any());
    }

    @Test
    void budgetNearLimitRendersText() {
        BudgetNearLimitEvent event = new BudgetNearLimitEvent(
                1L, 2L, "Comida", new BigDecimal("120.00"), new BigDecimal("100.00"), 80, "PEN",
                when, new BigDecimal("25.50"), "Efectivo");

        RenderedNotification rendered = formatter.budgetNearLimit(event, user);

        assertThat(rendered.text()).isEqualTo(
                "Has alcanzado tu porcentaje de alerta (80%) en tu presupuesto para: Comida. Gastado: 120.00 PEN de 100.00 PEN.");
    }

    @Test
    void savingGoalCompletedRendersText() {
        SavingGoalCompletedEvent event = new SavingGoalCompletedEvent(
                1L, 2L, "Viaje", new BigDecimal("500.00"), new BigDecimal("500.00"), "PEN",
                when, new BigDecimal("50.00"));

        RenderedNotification rendered = formatter.savingGoalCompleted(event, user);

        assertThat(rendered.text()).isEqualTo(
                "¡Felicitaciones! Has alcanzado tu meta de ahorro 'Viaje' con un total acumulado de 500.00 PEN de 500.00 PEN.");
    }
}
