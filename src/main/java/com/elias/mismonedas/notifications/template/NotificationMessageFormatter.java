package com.elias.mismonedas.notifications.template;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.shared.mapper.DateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class NotificationMessageFormatter {

    private final DateMapper dateMapper;

    public String budgetExceeded(BudgetExceededEvent event, User user) {
        ZoneId zone = user.getTimeZone().toZoneId();
        return "Has superado el límite de tu presupuesto para: " + event.categoryName() +
                ".\n Total gastado: " + event.spent() +
                " de " + event.limitAmount() +
                ".\n Último movimiento:" +
                "\n Fecha: " + dateMapper.toStringES(event.lastTransactionAt(), zone) +
                ",\n Monto: " + event.lastTransactionAmount() +
                ",\n Método: " + event.lastTransactionMethod();
    }

    public String budgetNearLimit(BudgetNearLimitEvent event, User user) {
        ZoneId zone = user.getTimeZone().toZoneId();
        return "Has alcanzado tu porcentaje de alerta de presupuesto para: " + event.categoryName() +
                ".\n Total: " + event.spent() +
                ".\n Último movimiento:" +
                "\n Fecha: " + dateMapper.toStringES(event.lastTransactionAt(), zone) +
                ",\n Monto: " + event.lastTransactionAmount() +
                ",\n Método: " + event.lastTransactionMethod();
    }

    public String savingGoalCompleted(SavingGoalCompletedEvent event, User user) {
        ZoneId zone = user.getTimeZone().toZoneId();
        return "🎉 ¡Felicitaciones! Has alcanzado tu meta de ahorro '" +
                event.description() +
                "' con un total acumulado de " +
                event.accumulated() +
                " " +
                event.currencyCode() +
                " de " +
                event.targetAmount() +
                ". El último movimiento fue de " +
                event.lastTransactionAmount() +
                " el " +
                dateMapper.toStringES(event.lastTransactionAt(), zone) +
                ". ¡Excelente disciplina financiera!";
    }
}
