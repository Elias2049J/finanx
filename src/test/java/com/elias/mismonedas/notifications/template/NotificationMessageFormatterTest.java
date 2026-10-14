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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMessageFormatterTest {

    @Mock private DateMapper dateMapper;

    private NotificationMessageFormatter formatter;
    private final User user = new User();
    private final OffsetDateTime when = OffsetDateTime.parse("2024-01-15T10:30:00-05:00");

    @BeforeEach
    void setUp() {
        formatter = new NotificationMessageFormatter(dateMapper);
        when(dateMapper.toStringES(any(OffsetDateTime.class), any(ZoneId.class))).thenReturn("FECHA");
    }

    @Test
    void budgetExceededReproducesCurrentText() {
        BudgetExceededEvent event = new BudgetExceededEvent(
                1L, 2L, "Comida",
                new BigDecimal("120.00"), new BigDecimal("100.00"),
                OffsetDateTime.now(), new BigDecimal("25.50"), "Efectivo");

        assertThat(formatter.budgetExceeded(event, user)).isEqualTo(
                "Has superado el límite de tu presupuesto para: Comida.\n Total gastado: 120.00 de 100.00" +
                        ".\n Último movimiento:" +
                        "\n Fecha: FECHA,\n Monto: 25.50,\n Método: Efectivo");
    }

    @Test
    void budgetNearLimitReproducesCurrentText() {
        BudgetNearLimitEvent event = new BudgetNearLimitEvent(
                1L, 2L, "Comida", new BigDecimal("120.00"), new BigDecimal("100.00"), 80,
                OffsetDateTime.now(), new BigDecimal("25.50"), "Efectivo");

        assertThat(formatter.budgetNearLimit(event, user))
                .isEqualTo("Has alcanzado tu porcentaje de alerta de presupuesto para: Comida" +
                        ".\n Total: 120.00" +
                        ".\n Último movimiento:" +
                        "\n Fecha: FECHA,\n Monto: 25.50,\n Método: Efectivo");
    }

    @Test
    void savingGoalCompletedReproducesCurrentTextIncludingEmoji() {
        SavingGoalCompletedEvent event = new SavingGoalCompletedEvent(
                1L, 2L, "Viaje", new BigDecimal("500.00"), new BigDecimal("500.00"), "PEN",
                OffsetDateTime.now(), new BigDecimal("50.00"));

        assertThat(formatter.savingGoalCompleted(event, user))
                .isEqualTo("🎉 ¡Felicitaciones! Has alcanzado tu meta de ahorro 'Viaje'" +
                        " con un total acumulado de 500.00" +
                        " PEN" +
                        " de 500.00" +
                        ". El último movimiento fue de 50.00" +
                        " el FECHA" +
                        ". ¡Excelente disciplina financiera!");
    }
}
