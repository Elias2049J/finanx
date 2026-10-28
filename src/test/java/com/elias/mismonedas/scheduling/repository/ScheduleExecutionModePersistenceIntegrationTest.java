package com.elias.mismonedas.scheduling.repository;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.ExecutionMode;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
class ScheduleExecutionModePersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RecurrenceRuleRepository recurrenceRuleRepository;

    @Autowired
    private TransactionScheduleRepository transactionScheduleRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void defaultsToManualWhenExecutionModeIsNotProvided() {
        TransactionSchedule schedule = persistSchedule(null);

        entityManager.clear();

        TransactionSchedule reloaded = transactionScheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getExecutionMode()).isEqualTo(ExecutionMode.MANUAL);
    }

    @Test
    @Transactional
    void persistsAutoExecutionModeWhenProvided() {
        TransactionSchedule schedule = persistSchedule(ExecutionMode.AUTO);

        entityManager.clear();

        TransactionSchedule reloaded = transactionScheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getExecutionMode()).isEqualTo(ExecutionMode.AUTO);
    }

    @Test
    @Transactional
    void acceptsPendingConfirmationState() {
        TransactionSchedule schedule = persistSchedule(null);
        schedule.setState(ScheduleState.PENDING_CONFIRMATION);

        transactionScheduleRepository.saveAndFlush(schedule);

        entityManager.clear();

        TransactionSchedule reloaded = transactionScheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ScheduleState.PENDING_CONFIRMATION);
    }

    @Test
    @Transactional
    void marksDueOccurrenceAsPendingConfirmation() {
        TransactionSchedule schedule = persistSchedule(ExecutionMode.MANUAL);
        OffsetDateTime dueAt = OffsetDateTime.now().minusMinutes(5);
        schedule.setNextRunAt(dueAt);
        transactionScheduleRepository.saveAndFlush(schedule);

        int updated = scheduleRepository.markPendingConfirmation(schedule.getId(), dueAt);

        assertThat(updated).isEqualTo(1);
        entityManager.clear();
        TransactionSchedule reloaded = transactionScheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ScheduleState.PENDING_CONFIRMATION);
        assertThat(reloaded.getNextRunAt()).isCloseTo(dueAt, within(1, ChronoUnit.MICROS));
    }

    @Test
    @Transactional
    void doesNotMarkPendingWhenOccurrenceNoLongerMatches() {
        TransactionSchedule schedule = persistSchedule(ExecutionMode.MANUAL);
        OffsetDateTime dueAt = OffsetDateTime.now().minusMinutes(5);
        schedule.setNextRunAt(dueAt);
        transactionScheduleRepository.saveAndFlush(schedule);

        int updated = scheduleRepository.markPendingConfirmation(schedule.getId(), dueAt.plusHours(1));

        assertThat(updated).isZero();
        entityManager.clear();
        TransactionSchedule reloaded = transactionScheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ScheduleState.RUNNING);
    }

    @Test
    @Transactional
    void claimsPendingOccurrenceOnlyOnce() {
        TransactionSchedule schedule = persistSchedule(ExecutionMode.MANUAL);
        OffsetDateTime dueAt = OffsetDateTime.now().minusMinutes(5);
        schedule.setNextRunAt(dueAt);
        transactionScheduleRepository.saveAndFlush(schedule);
        scheduleRepository.markPendingConfirmation(schedule.getId(), dueAt);

        int first = scheduleRepository.claimPendingConfirmation(schedule.getId(), dueAt);
        int second = scheduleRepository.claimPendingConfirmation(schedule.getId(), dueAt);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        entityManager.clear();
        TransactionSchedule reloaded = transactionScheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ScheduleState.RUNNING);
        assertThat(reloaded.getNextRunAt()).isCloseTo(dueAt, within(1, ChronoUnit.MICROS));
    }

    @Test
    @Transactional
    void doesNotClaimPendingOccurrenceWhenRunAtChanged() {
        TransactionSchedule schedule = persistSchedule(ExecutionMode.MANUAL);
        OffsetDateTime dueAt = OffsetDateTime.now().minusMinutes(5);
        schedule.setNextRunAt(dueAt);
        transactionScheduleRepository.saveAndFlush(schedule);
        scheduleRepository.markPendingConfirmation(schedule.getId(), dueAt);

        int updated = scheduleRepository.claimPendingConfirmation(schedule.getId(), dueAt.plusHours(1));

        assertThat(updated).isZero();
        entityManager.clear();
        TransactionSchedule reloaded = transactionScheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ScheduleState.PENDING_CONFIRMATION);
    }

    private TransactionSchedule persistSchedule(ExecutionMode executionMode) {
        User user = new User();
        user.setName("Reminder");
        user.setLastname("Fixture");
        user.setEmail("reminder-" + UUID.randomUUID() + "@example.test");
        user.setPassword("not-used");
        user.setRole(Role.USER);
        User savedUser = userRepository.saveAndFlush(user);

        Category category = new Category();
        category.setName("Recordatorio");
        category.setType(TransactionType.SPENT);
        category.setUser(savedUser);
        Category savedCategory = categoryRepository.saveAndFlush(category);

        Wallet wallet = Wallet.builder()
                .user(savedUser)
                .currencyCode("PEN")
                .alias("Principal")
                .balance(BigDecimal.ZERO)
                .primary(true)
                .build();
        Wallet savedWallet = walletRepository.saveAndFlush(wallet);

        RecurrenceRule rule = new RecurrenceRule();
        rule.setRecurrenceType(RecurrenceType.MONTHLY);
        rule.setInterval(1);
        rule.setStart(OffsetDateTime.now());
        rule.setUser(savedUser);
        RecurrenceRule savedRule = recurrenceRuleRepository.saveAndFlush(rule);

        TransactionSchedule schedule = new TransactionSchedule();
        schedule.setUser(savedUser);
        schedule.setCategory(savedCategory);
        schedule.setNextRunAt(OffsetDateTime.now());
        schedule.setRecurrenceRule(savedRule);
        schedule.setWallet(savedWallet);
        schedule.setAmount(new BigDecimal("100.00"));
        schedule.setPaymentMethod(PaymentMethod.CASH);
        schedule.setTransactionType(TransactionType.SPENT);
        schedule.setExecutionMode(executionMode);
        return transactionScheduleRepository.saveAndFlush(schedule);
    }
}
