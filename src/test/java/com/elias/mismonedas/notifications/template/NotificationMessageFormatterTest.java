package com.elias.mismonedas.notifications.template;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.planning.domain.event.BudgetExceededEvent;
import com.elias.mismonedas.planning.domain.event.BudgetNearLimitEvent;
import com.elias.mismonedas.planning.domain.event.BudgetRegisteredEvent;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.ExecutionMode;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.elias.mismonedas.shared.mapper.DateMapper;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
        JsonMessageCatalog messageCatalog = new JsonMessageCatalog(new ObjectMapper());
        messageCatalog.initialize();
        formatter = new NotificationMessageFormatter(dateMapper, templateEngine, messageCatalog);
        lenient().when(dateMapper.toStringLocalized(any(OffsetDateTime.class), any(ZoneId.class), any(Locale.class)))
                .thenReturn("FECHA");
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
    void budgetExceededUsesEnglishFromUserPreference() {
        user.setPreferences(UserPreferences.builder()
                .locale("en-US")
                .timeZone(TimeZone.AMERICA_LIMA)
                .build());
        BudgetExceededEvent event = new BudgetExceededEvent(
                1L, 2L, "Food", new BigDecimal("120.00"), new BigDecimal("100.00"), "PEN",
                when, new BigDecimal("25.50"), "Cash");

        RenderedNotification rendered = formatter.budgetExceeded(event, user);

        assertThat(rendered.text()).isEqualTo(
                "You exceeded your budget limit for: Food. Spent: 120.00 PEN of 100.00 PEN. Last transaction: 25.50 PEN (Cash).");
        verify(templateEngine).process(eq("notifications/budget-exceeded"), any());
    }

    @Test
    void htmlTemplateRendersInTheUsersLocale() {
        JsonMessageCatalog messageCatalog = new JsonMessageCatalog(new ObjectMapper());
        messageCatalog.initialize();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine realTemplateEngine = new SpringTemplateEngine();
        realTemplateEngine.setTemplateResolver(resolver);
        NotificationMessageFormatter realFormatter =
                new NotificationMessageFormatter(dateMapper, realTemplateEngine, messageCatalog);
        user.setPreferences(UserPreferences.builder()
                .locale("en-US")
                .timeZone(TimeZone.AMERICA_LIMA)
                .build());
        BudgetExceededEvent event = new BudgetExceededEvent(
                1L, 2L, "Food", new BigDecimal("120.00"), new BigDecimal("100.00"), "PEN",
                when, new BigDecimal("25.50"), "Cash");

        RenderedNotification rendered = realFormatter.budgetExceeded(event, user);

        assertThat(rendered.html()).contains("Budget exceeded", "Total spent", "This is an automated email")
                .doesNotContain("Presupuesto excedido", "Total gastado");
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
                "¡Felicitaciones! Has alcanzado tu meta de ahorro «Viaje» con un total acumulado de 500.00 PEN de 500.00 PEN.");
    }

    @Test
    void transactionSchedulePendingRendersActionableReminder() {
        TransactionSchedule schedule = new TransactionSchedule();
        schedule.setAmount(new BigDecimal("50.00"));
        schedule.setWallet(Wallet.builder().currencyCode("PEN").alias("BCP").build());
        Category category = new Category();
        category.setName("Comida");
        schedule.setCategory(category);

        RenderedNotification rendered = formatter.transactionSchedulePending(schedule, user);

        assertThat(rendered.title()).isEqualTo("Recordatorio");
        assertThat(rendered.text())
                .isEqualTo("Recordatorio pendiente: 50.00 PEN en Comida. Confírmalo en la app.");
        verify(templateEngine).process(eq("notifications/schedule-reminder"), any());
    }

    @Test
    void budgetSchedulePendingRendersActionableReminder() {
        BudgetSchedule schedule = new BudgetSchedule();
        schedule.setDescription("Transporte");

        RenderedNotification rendered = formatter.budgetSchedulePending(schedule, user);

        assertThat(rendered.title()).isEqualTo("Recordatorio");
        assertThat(rendered.text())
                .isEqualTo("Recordatorio pendiente para el presupuesto «Transporte». Confírmalo en la app.");
        verify(templateEngine).process(eq("notifications/schedule-reminder"), any());
    }

    @Test
    void transactionScheduleExecutedDistinguishesAutoFromManual() {
        TransactionRegisteredEvent event =
                new TransactionRegisteredEvent(
                        1L, new BigDecimal("50.00"), 2L, 3L, TransactionType.SPENT, 4L, 5L, null);

        RenderedNotification auto = formatter.transactionScheduleExecuted(event, user, ExecutionMode.AUTO);
        RenderedNotification manual = formatter.transactionScheduleExecuted(event, user, ExecutionMode.MANUAL);

        assertThat(auto.title()).isEqualTo("Movimiento registrado");
        assertThat(auto.text()).isEqualTo("Se registró automáticamente un movimiento de 50.00.");
        assertThat(manual.text()).isEqualTo("Confirmaste el recordatorio: se registró un movimiento de 50.00.");
    }

    @Test
    void budgetScheduleExecutedDistinguishesAutoFromManual() {
        BudgetRegisteredEvent event = new BudgetRegisteredEvent(1L, 2L, 3L);

        RenderedNotification auto = formatter.budgetScheduleExecuted(event, user, ExecutionMode.AUTO, "Transporte");
        RenderedNotification manual = formatter.budgetScheduleExecuted(event, user, ExecutionMode.MANUAL, "Transporte");

        assertThat(auto.title()).isEqualTo("Presupuesto registrado");
        assertThat(auto.text()).isEqualTo("Se registró automáticamente el presupuesto «Transporte».");
        assertThat(manual.text()).isEqualTo("Confirmaste el recordatorio del presupuesto «Transporte».");
    }
}
