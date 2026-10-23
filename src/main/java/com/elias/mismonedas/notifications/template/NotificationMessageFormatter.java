package com.elias.mismonedas.notifications.template;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.shared.mapper.DateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationMessageFormatter {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private final DateMapper dateMapper;
    private final SpringTemplateEngine templateEngine;

    public RenderedNotification budgetExceeded(BudgetExceededEvent event, User user) {
        ZoneId zone = user.getTimeZone().toZoneId();
        String currency = event.currencyCode() == null ? "" : event.currencyCode();

        Map<String, Object> vars = new HashMap<>();
        vars.put("categoryName", event.categoryName());
        vars.put("spent", money(event.spent()));
        vars.put("limitAmount", money(event.limitAmount()));
        vars.put("currency", currency);
        vars.put("date", dateMapper.toStringES(event.lastTransactionAt(), zone));
        vars.put("lastAmount", money(event.lastTransactionAmount()));
        vars.put("method", event.lastTransactionMethod());

        String text = "Has superado el límite de tu presupuesto para: " + event.categoryName()
                + ". Gastado: " + money(event.spent()) + " " + currency
                + " de " + money(event.limitAmount()) + " " + currency
                + ". Último movimiento: " + money(event.lastTransactionAmount()) + " " + currency
                + " (" + event.lastTransactionMethod() + ").";

        return new RenderedNotification(text, render("budget-exceeded", vars));
    }

    public RenderedNotification budgetNearLimit(BudgetNearLimitEvent event, User user) {
        ZoneId zone = user.getTimeZone().toZoneId();
        String currency = event.currencyCode() == null ? "" : event.currencyCode();

        Map<String, Object> vars = new HashMap<>();
        vars.put("categoryName", event.categoryName());
        vars.put("spent", money(event.spent()));
        vars.put("limitAmount", money(event.limitAmount()));
        vars.put("currency", currency);
        vars.put("percentAlert", event.percentAlert());
        vars.put("date", dateMapper.toStringES(event.lastTransactionAt(), zone));
        vars.put("lastAmount", money(event.lastTransactionAmount()));
        vars.put("method", event.lastTransactionMethod());

        String text = "Has alcanzado tu porcentaje de alerta (" + event.percentAlert()
                + "%) en tu presupuesto para: " + event.categoryName()
                + ". Gastado: " + money(event.spent()) + " " + currency
                + " de " + money(event.limitAmount()) + " " + currency + ".";

        return new RenderedNotification(text, render("budget-near-limit", vars));
    }

    public RenderedNotification savingGoalCompleted(SavingGoalCompletedEvent event, User user) {
        ZoneId zone = user.getTimeZone().toZoneId();
        String currency = event.currencyCode() == null ? "" : event.currencyCode();

        Map<String, Object> vars = new HashMap<>();
        vars.put("description", event.description());
        vars.put("accumulated", money(event.accumulated()));
        vars.put("targetAmount", money(event.targetAmount()));
        vars.put("currency", currency);
        vars.put("date", dateMapper.toStringES(event.lastTransactionAt(), zone));
        vars.put("lastAmount", money(event.lastTransactionAmount()));

        String text = "¡Felicitaciones! Has alcanzado tu meta de ahorro '" + event.description()
                + "' con un total acumulado de " + money(event.accumulated()) + " " + currency
                + " de " + money(event.targetAmount()) + " " + currency + ".";

        return new RenderedNotification(text, render("saving-goal-completed", vars));
    }

    public RenderedNotification transactionScheduleExecuted(TransactionRegisteredEvent event) {
        String text = "Se ha registrado un nuevo movimiento automático.";
        Map<String, Object> vars = new HashMap<>();
        vars.put("amount", money(event.amount()));
        return new RenderedNotification(text, render("schedule-transaction", vars));
    }

    public RenderedNotification budgetScheduleExecuted(BudgetRegisteredEvent event) {
        String text = "Se ha registrado un nuevo presupuesto automático.";
        return new RenderedNotification(text, render("schedule-budget", new HashMap<>()));
    }

    private String render(String template, Map<String, Object> vars) {
        return templateEngine.process("notifications/" + template, new Context(Locale.getDefault(), vars));
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : MONEY.format(value);
    }
}
