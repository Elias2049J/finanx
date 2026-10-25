package com.elias.mismonedas.scheduling.repository;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.messaging.rabbitmq.consumer-auto-startup=false")
class ScheduleClaimConcurrencyTest {

    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private TransactionScheduleRepository transactionScheduleRepository;
    @Autowired private BudgetScheduleRepository budgetScheduleRepository;
    @Autowired private RecurrenceRuleRepository recurrenceRuleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private User user;
    private Wallet wallet;
    private Category category;
    private RecurrenceRule transactionRule;
    private RecurrenceRule budgetRule;
    private TransactionSchedule transactionSchedule;
    private BudgetSchedule budgetSchedule;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("claim-" + UUID.randomUUID() + "@example.test");
        user.setPassword("test-password");
        user = userRepository.save(user);

        wallet = walletRepository.save(Wallet.builder()
                .user(user)
                .currencyCode("PEN")
                .balance(BigDecimal.ZERO)
                .primary(true)
                .build());

        category = new Category();
        category.setUser(user);
        category.setName("Claim test");
        category.setType(TransactionType.INCOME);
        category.setDescription("Claim concurrency test");
        category = categoryRepository.save(category);

        transactionRule = rule();
        budgetRule = rule();

        OffsetDateTime nextRunAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).plusDays(1);

        transactionSchedule = new TransactionSchedule();
        transactionSchedule.setUser(user);
        transactionSchedule.setCategory(category);
        transactionSchedule.setWallet(wallet);
        transactionSchedule.setAmount(new BigDecimal("10.00"));
        transactionSchedule.setPaymentMethod(PaymentMethod.CASH);
        transactionSchedule.setTransactionType(TransactionType.INCOME);
        transactionSchedule.setNextRunAt(nextRunAt);
        transactionSchedule.setActive(true);
        transactionSchedule.setState(ScheduleState.RUNNING);
        transactionSchedule.setRecurrenceRule(transactionRule);
        transactionSchedule = transactionScheduleRepository.save(transactionSchedule);
        transactionSchedule = transactionScheduleRepository.findById(transactionSchedule.getId()).orElseThrow();

        budgetSchedule = new BudgetSchedule();
        budgetSchedule.setUser(user);
        budgetSchedule.setCategory(category);
        budgetSchedule.setLimitAmount(new BigDecimal("100.00"));
        budgetSchedule.setNextRunAt(nextRunAt);
        budgetSchedule.setActive(true);
        budgetSchedule.setState(ScheduleState.RUNNING);
        budgetSchedule.setRecurrenceRule(budgetRule);
        budgetSchedule = budgetScheduleRepository.save(budgetSchedule);
        budgetSchedule = budgetScheduleRepository.findById(budgetSchedule.getId()).orElseThrow();
    }

    private RecurrenceRule rule() {
        RecurrenceRule rule = new RecurrenceRule();
        rule.setUser(user);
        rule.setRecurrenceType(RecurrenceType.DAILY);
        rule.setInterval(1);
        rule.setStart(OffsetDateTime.now(user.getTimeZone().toZoneId()).minusDays(1));
        rule.setEnd(OffsetDateTime.now(user.getTimeZone().toZoneId()).plusDays(30));
        return recurrenceRuleRepository.save(rule);
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            transactionScheduleRepository.deleteById(transactionSchedule.getId());
            budgetScheduleRepository.deleteById(budgetSchedule.getId());
            recurrenceRuleRepository.findById(transactionRule.getId()).ifPresent(recurrenceRuleRepository::delete);
            recurrenceRuleRepository.findById(budgetRule.getId()).ifPresent(recurrenceRuleRepository::delete);
            categoryRepository.deleteById(category.getId());
            walletRepository.deleteById(wallet.getId());
            userRepository.deleteById(user.getId());
        });
    }

    @Test
    void shouldClaimTransactionOccurrenceOnlyOnce() throws Exception {
        assertClaimedOnlyOnce(
                transactionSchedule.getId(),
                transactionSchedule.getNextRunAt(),
                transactionScheduleRepository::findById,
                transactionScheduleRepository::save);
    }

    @Test
    void shouldClaimBudgetOccurrenceOnlyOnce() throws Exception {
        assertClaimedOnlyOnce(
                budgetSchedule.getId(),
                budgetSchedule.getNextRunAt(),
                budgetScheduleRepository::findById,
                budgetScheduleRepository::save);
    }

    @Test
    void shouldPersistAdvancedNextRunAt() {
        OffsetDateTime expected = transactionSchedule.getNextRunAt();
        Integer claimed = transactionTemplate.execute(status -> {
            TransactionSchedule managed = transactionScheduleRepository.findById(transactionSchedule.getId()).orElseThrow();
            int result = scheduleRepository.claimOccurrence(managed.getId(), expected, ScheduleState.RUNNING.name());
            managed.setLastRunAt(expected);
            managed.setNextRunAt(expected.plusDays(1));
            transactionScheduleRepository.save(managed);
            return result;
        });

        assertThat(claimed).isEqualTo(1);
        TransactionSchedule reloaded = transactionScheduleRepository.findById(transactionSchedule.getId()).orElseThrow();
        assertThat(reloaded.getNextRunAt().toInstant()).isAfter(expected.toInstant());

        Integer second = transactionTemplate.execute(status ->
                scheduleRepository.claimOccurrence(transactionSchedule.getId(), expected, ScheduleState.RUNNING.name()));
        assertThat(second).isZero();
    }

    @Test
    void shouldNotClaimPausedSchedule() {
        transactionSchedule.setState(ScheduleState.PAUSED);
        transactionScheduleRepository.save(transactionSchedule);

        Integer claimed = transactionTemplate.execute(status ->
                scheduleRepository.claimOccurrence(
                        transactionSchedule.getId(),
                        transactionSchedule.getNextRunAt(),
                        ScheduleState.RUNNING.name()));

        assertThat(claimed).isZero();
    }

    private <T extends Schedule> void assertClaimedOnlyOnce(
            Long id,
            OffsetDateTime expected,
            LongFunction<Optional<T>> finder,
            ScheduleStore<T> saver) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);

        Future<Integer> first = pool.submit(() -> transactionTemplate.execute(status -> {
            T managed = finder.apply(id).orElseThrow();
            int claimed = scheduleRepository.claimOccurrence(id, expected, ScheduleState.RUNNING.name());
            firstClaimed.countDown();
            try {
                commitFirst.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            managed.setLastRunAt(expected);
            managed.setNextRunAt(expected.plusDays(1));
            saver.save(managed);
            return claimed;
        }));

        assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();

        Future<Integer> second = pool.submit(() -> transactionTemplate.execute(status ->
                scheduleRepository.claimOccurrence(id, expected, ScheduleState.RUNNING.name())));

        Thread.sleep(500);
        commitFirst.countDown();

        assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(second.get(15, TimeUnit.SECONDS)).isZero();
        pool.shutdown();
    }

    @FunctionalInterface
    private interface ScheduleStore<T extends Schedule> {
        T save(T schedule);
    }
}
