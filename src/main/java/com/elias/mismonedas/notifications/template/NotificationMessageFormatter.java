package com.elias.mismonedas.notifications.template;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.SupportedLocale;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.elias.mismonedas.shared.mapper.DateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationMessageFormatter {

    private final DateMapper dateMapper;
    private final SpringTemplateEngine templateEngine;
    private final JsonMessageCatalog messageCatalog;

    public RenderedNotification budgetExceeded(BudgetExceededEvent event, User user) {
        Locale locale = localeOf(user);
        ZoneId zone = user.getTimeZone().toZoneId();
        String currency = event.currencyCode() == null ? "" : event.currencyCode();
        String spent = money(event.spent(), locale);
        String limitAmount = money(event.limitAmount(), locale);
        String lastAmount = money(event.lastTransactionAmount(), locale);
        String date = dateMapper.toStringLocalized(event.lastTransactionAt(), zone, locale);

        Map<String, Object> vars = new HashMap<>();
        vars.put("heading", message("notification.budget.exceeded.heading", locale));
        vars.put("intro", message("notification.budget.exceeded.intro", locale, event.categoryName()));
        vars.put("totalLabel", message("notification.budget.exceeded.total", locale));
        vars.put("ofLabel", message("notification.common.of", locale));
        vars.put("lastMovementText", message("notification.common.last_movement.details", locale,
                lastAmount, currency, event.lastTransactionMethod(), date));
        vars.put("disclaimer", message("notification.common.disclaimer", locale));
        vars.put("spent", spent);
        vars.put("limitAmount", limitAmount);
        vars.put("currency", currency);

        String text = message("notification.budget.exceeded.text", locale,
                event.categoryName(), spent, currency, limitAmount, lastAmount, event.lastTransactionMethod());
        return new RenderedNotification(text, render("budget-exceeded", vars, locale));
    }

    public RenderedNotification budgetNearLimit(BudgetNearLimitEvent event, User user) {
        Locale locale = localeOf(user);
        ZoneId zone = user.getTimeZone().toZoneId();
        String currency = event.currencyCode() == null ? "" : event.currencyCode();
        String spent = money(event.spent(), locale);
        String limitAmount = money(event.limitAmount(), locale);
        String lastAmount = money(event.lastTransactionAmount(), locale);
        String date = dateMapper.toStringLocalized(event.lastTransactionAt(), zone, locale);

        Map<String, Object> vars = new HashMap<>();
        vars.put("heading", message("notification.budget.near_limit.heading", locale));
        vars.put("intro", message("notification.budget.near_limit.intro", locale,
                event.percentAlert(), event.categoryName()));
        vars.put("totalLabel", message("notification.budget.near_limit.total", locale));
        vars.put("ofLabel", message("notification.common.of", locale));
        vars.put("lastMovementText", message("notification.common.last_movement.details", locale,
                lastAmount, currency, event.lastTransactionMethod(), date));
        vars.put("disclaimer", message("notification.common.disclaimer", locale));
        vars.put("spent", spent);
        vars.put("limitAmount", limitAmount);
        vars.put("currency", currency);

        String text = message("notification.budget.near_limit.text", locale,
                event.percentAlert(), event.categoryName(), spent, currency, limitAmount);
        return new RenderedNotification(text, render("budget-near-limit", vars, locale));
    }

    public RenderedNotification savingGoalCompleted(SavingGoalCompletedEvent event, User user) {
        Locale locale = localeOf(user);
        ZoneId zone = user.getTimeZone().toZoneId();
        String currency = event.currencyCode() == null ? "" : event.currencyCode();
        String accumulated = money(event.accumulated(), locale);
        String targetAmount = money(event.targetAmount(), locale);
        String lastAmount = money(event.lastTransactionAmount(), locale);
        String date = dateMapper.toStringLocalized(event.lastTransactionAt(), zone, locale);

        Map<String, Object> vars = new HashMap<>();
        vars.put("heading", message("notification.saving_goal.completed.heading", locale));
        vars.put("intro", message("notification.saving_goal.completed.intro", locale, event.description()));
        vars.put("accumulatedLabel", message("notification.saving_goal.completed.accumulated", locale));
        vars.put("targetLabel", message("notification.saving_goal.completed.target", locale));
        vars.put("lastContributionText", message("notification.saving_goal.completed.last_contribution.details", locale,
                lastAmount, currency, date));
        vars.put("disclaimer", message("notification.common.disclaimer", locale));
        vars.put("accumulated", accumulated);
        vars.put("targetAmount", targetAmount);
        vars.put("currency", currency);

        String text = message("notification.saving_goal.completed.text", locale,
                event.description(), accumulated, currency, targetAmount);
        return new RenderedNotification(text, render("saving-goal-completed", vars, locale));
    }

    public RenderedNotification transactionScheduleExecuted(TransactionRegisteredEvent event, User user) {
        Locale locale = localeOf(user);
        String amount = money(event.amount(), locale);
        Map<String, Object> vars = new HashMap<>();
        vars.put("heading", message("notification.schedule.transaction.heading", locale));
        vars.put("intro", message("notification.schedule.transaction.intro", locale, amount));
        vars.put("disclaimer", message("notification.common.disclaimer", locale));
        return new RenderedNotification(
                message("notification.schedule.transaction.text", locale),
                render("schedule-transaction", vars, locale));
    }

    public RenderedNotification budgetScheduleExecuted(BudgetRegisteredEvent event, User user) {
        Locale locale = localeOf(user);
        Map<String, Object> vars = new HashMap<>();
        vars.put("heading", message("notification.schedule.budget.heading", locale));
        vars.put("intro", message("notification.schedule.budget.intro", locale));
        vars.put("disclaimer", message("notification.common.disclaimer", locale));
        return new RenderedNotification(
                message("notification.schedule.budget.text", locale),
                render("schedule-budget", vars, locale));
    }

    private String render(String template, Map<String, Object> vars, Locale locale) {
        return templateEngine.process("notifications/" + template, new Context(locale, vars));
    }

    private String message(String key, Locale locale, Object... arguments) {
        return messageCatalog.getMessage(key, locale, arguments);
    }

    private static Locale localeOf(User user) {
        return SupportedLocale.toLocale(
                user.getPreferences() == null ? null : user.getPreferences().getLocale());
    }

    private static String money(BigDecimal value, Locale locale) {
        NumberFormat formatter = NumberFormat.getNumberInstance(locale);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(value == null ? BigDecimal.ZERO : value);
    }
}
