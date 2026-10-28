package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.service.TransactionService;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.ExecutionMode;
import com.elias.mismonedas.scheduling.domain.enums.OccurrenceStatus;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceResponse;
import com.elias.mismonedas.scheduling.mapper.ScheduleMapper;
import com.elias.mismonedas.scheduling.repository.BudgetScheduleRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import com.elias.mismonedas.scheduling.repository.TransactionScheduleRepository;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import com.elias.mismonedas.wallets.service.WalletService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceImplOccurrencesTest {

    private static final ZoneOffset LIMA_OFFSET = ZoneOffset.ofHours(-5);

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TransactionScheduleRepository tsRepository;
    @Mock private BudgetScheduleRepository bsRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleMapper tsMapper;
    @Mock private BudgetRepository budgetRepository;
    @Mock private RecurrenceService rcService;
    @Mock private ReasonResolver reasonResolver;
    @Mock private RecurrenceService recurrenceService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TransactionService transactionService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private EntityManager entityManager;
    @Mock private WalletService walletService;
    @Mock private WalletRepository walletRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private SecurityService securityService;

    private ScheduleServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ScheduleServiceImpl(
                scheduleRepository, tsRepository, bsRepository, categoryRepository, tsMapper,
                budgetRepository, rcService, reasonResolver, recurrenceService, eventPublisher,
                transactionService, transactionRepository, entityManager, walletService, walletRepository,
                transactionTemplate, securityService);

        user = user();
        when(securityService.getCurrentUser()).thenReturn(user);
        when(walletRepository.findByUserIdAndPrimaryTrue(1L)).thenReturn(Optional.of(wallet(user, "PEN")));
        when(tsRepository.findAllByUser_IdAndActiveAndState(eq(1L), eq(true), any()))
                .thenReturn(List.of());
        when(bsRepository.findAllByUser_IdAndActiveAndState(eq(1L), eq(true), any()))
                .thenReturn(List.of());
    }

    @Test
    void projectsRunningOccurrencesInsideRangeWithoutOccurrenceKey() {
        OffsetDateTime base = OffsetDateTime.of(2026, 10, 6, 9, 0, 0, 0, LIMA_OFFSET);
        TransactionSchedule schedule = transactionSchedule(user, base, ScheduleState.RUNNING);
        stubRunningTransactions(schedule);
        stubWeeklyAdvance(schedule);

        List<OccurrenceResponse> result = service.listOccurrences(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(result).hasSize(4);
        assertThat(result).extracting(OccurrenceResponse::getOccurrenceAt).containsExactly(
                LocalDateTime.of(2026, 10, 6, 9, 0),
                LocalDateTime.of(2026, 10, 13, 9, 0),
                LocalDateTime.of(2026, 10, 20, 9, 0),
                LocalDateTime.of(2026, 10, 27, 9, 0));
        assertThat(result).allSatisfy(occurrence -> {
            assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.UPCOMING);
            assertThat(occurrence.getOccurrenceKey()).isNull();
            assertThat(occurrence.getKind()).isEqualTo("TRANSACTION");
            assertThat(occurrence.getScheduleId()).isEqualTo(55L);
            assertThat(occurrence.getCurrencyCode()).isEqualTo("PEN");
            assertThat(occurrence.getTransactionType()).isEqualTo(TransactionType.SPENT);
            assertThat(occurrence.getCategoryName()).isEqualTo("Servicios");
        });
    }

    @Test
    void marksPendingOccurrenceWithKeyAndProjectsFollowingOnes() {
        OffsetDateTime pendingAt = OffsetDateTime.of(2026, 10, 3, 9, 0, 0, 0, LIMA_OFFSET);
        TransactionSchedule schedule = transactionSchedule(user, pendingAt, ScheduleState.PENDING_CONFIRMATION);
        stubPendingTransactions(schedule);
        stubWeeklyAdvance(schedule);

        List<OccurrenceResponse> result = service.listOccurrences(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(result).hasSize(5);
        OccurrenceResponse pending = result.get(0);
        assertThat(pending.getStatus()).isEqualTo(OccurrenceStatus.PENDING);
        assertThat(pending.getOccurrenceAt()).isEqualTo(LocalDateTime.of(2026, 10, 3, 9, 0));
        assertThat(pending.getOccurrenceKey())
                .isEqualTo("schedule:55:" + pendingAt.toInstant().toEpochMilli());
        assertThat(result.subList(1, result.size()))
                .allSatisfy(occurrence -> {
                    assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.UPCOMING);
                    assertThat(occurrence.getOccurrenceKey()).isNull();
                });
    }

    @Test
    void projectsBudgetOccurrencesWithPrimaryCurrency() {
        OffsetDateTime base = OffsetDateTime.of(2026, 10, 1, 8, 0, 0, 0, LIMA_OFFSET);
        BudgetSchedule budget = budgetSchedule(user, base, ScheduleState.RUNNING);
        when(bsRepository.findAllByUser_IdAndActiveAndState(1L, true, ScheduleState.RUNNING))
                .thenReturn(List.of(budget));
        when(rcService.computeNextRunAt(eq(budget), any()))
                .thenAnswer(invocation -> Optional.of(((OffsetDateTime) invocation.getArgument(1)).plusMonths(1)));

        List<OccurrenceResponse> result = service.listOccurrences(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(occurrence -> {
            assertThat(occurrence.getKind()).isEqualTo("BUDGET");
            assertThat(occurrence.getAmount()).isEqualByComparingTo("800.00");
            assertThat(occurrence.getCurrencyCode()).isEqualTo("PEN");
            assertThat(occurrence.getTransactionType()).isNull();
            assertThat(occurrence.getDescription()).isEqualTo("Presupuesto mensual");
        });
    }

    @Test
    void stopsProjectionWhenRecurrenceEnds() {
        OffsetDateTime base = OffsetDateTime.of(2026, 10, 1, 8, 0, 0, 0, LIMA_OFFSET);
        TransactionSchedule schedule = transactionSchedule(user, base, ScheduleState.RUNNING);
        stubRunningTransactions(schedule);
        when(rcService.computeNextRunAt(eq(schedule), any()))
                .thenReturn(Optional.of(base.plusWeeks(1)))
                .thenReturn(Optional.empty());

        List<OccurrenceResponse> result = service.listOccurrences(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));

        assertThat(result).hasSize(2);
    }

    @Test
    void sortsOccurrencesAscendingAcrossSchedules() {
        OffsetDateTime later = OffsetDateTime.of(2026, 10, 20, 9, 0, 0, 0, LIMA_OFFSET);
        OffsetDateTime earlier = OffsetDateTime.of(2026, 10, 5, 9, 0, 0, 0, LIMA_OFFSET);
        TransactionSchedule late = transactionSchedule(user, later, ScheduleState.RUNNING);
        TransactionSchedule early = transactionSchedule(user, earlier, ScheduleState.RUNNING);
        when(tsRepository.findAllByUser_IdAndActiveAndState(1L, true, ScheduleState.RUNNING))
                .thenReturn(List.of(late, early));
        when(rcService.computeNextRunAt(any(), any())).thenReturn(Optional.empty());

        List<OccurrenceResponse> result = service.listOccurrences(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(result).extracting(OccurrenceResponse::getOccurrenceAt).containsExactly(
                LocalDateTime.of(2026, 10, 5, 9, 0),
                LocalDateTime.of(2026, 10, 20, 9, 0));
    }

    @Test
    void usesUserTimeZoneForRangeBounds() {
        OffsetDateTime lastMinuteOfDay = OffsetDateTime.of(2026, 10, 1, 23, 30, 0, 0, LIMA_OFFSET);
        TransactionSchedule schedule = transactionSchedule(user, lastMinuteOfDay, ScheduleState.RUNNING);
        stubRunningTransactions(schedule);
        when(rcService.computeNextRunAt(eq(schedule), any())).thenReturn(Optional.empty());

        List<OccurrenceResponse> insideDay = service.listOccurrences(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));
        List<OccurrenceResponse> previousDay = service.listOccurrences(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30));

        assertThat(insideDay).hasSize(1);
        assertThat(previousDay).isEmpty();
    }

    @Test
    void capsProjectionAtFiveHundredOccurrences() {
        OffsetDateTime base = OffsetDateTime.of(2026, 1, 1, 9, 0, 0, 0, LIMA_OFFSET);
        TransactionSchedule schedule = transactionSchedule(user, base, ScheduleState.RUNNING);
        stubRunningTransactions(schedule);
        when(rcService.computeNextRunAt(eq(schedule), any()))
                .thenAnswer(invocation -> Optional.of(((OffsetDateTime) invocation.getArgument(1)).plusDays(1)));

        List<OccurrenceResponse> result = service.listOccurrences(
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31));

        assertThat(result).hasSize(500);
    }

    @Test
    void queriesOnlyRunningAndPendingConfirmationStates() {
        service.listOccurrences(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        verify(tsRepository).findAllByUser_IdAndActiveAndState(1L, true, ScheduleState.RUNNING);
        verify(tsRepository).findAllByUser_IdAndActiveAndState(1L, true, ScheduleState.PENDING_CONFIRMATION);
        verify(bsRepository).findAllByUser_IdAndActiveAndState(1L, true, ScheduleState.RUNNING);
        verify(bsRepository).findAllByUser_IdAndActiveAndState(1L, true, ScheduleState.PENDING_CONFIRMATION);
    }

    @Test
    void rejectsInvalidRange() {
        assertThatThrownBy(() -> service.listOccurrences(
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void stubRunningTransactions(TransactionSchedule... schedules) {
        when(tsRepository.findAllByUser_IdAndActiveAndState(1L, true, ScheduleState.RUNNING))
                .thenReturn(List.of(schedules));
    }

    private void stubPendingTransactions(TransactionSchedule... schedules) {
        when(tsRepository.findAllByUser_IdAndActiveAndState(1L, true, ScheduleState.PENDING_CONFIRMATION))
                .thenReturn(List.of(schedules));
    }

    private void stubWeeklyAdvance(TransactionSchedule schedule) {
        when(rcService.computeNextRunAt(eq(schedule), any()))
                .thenAnswer(invocation -> Optional.of(((OffsetDateTime) invocation.getArgument(1)).plusWeeks(1)));
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setPreferences(UserPreferences.builder().timeZone(TimeZone.AMERICA_LIMA).build());
        return user;
    }

    private Wallet wallet(User user, String currencyCode) {
        return Wallet.builder()
                .id(10L)
                .user(user)
                .currencyCode(currencyCode)
                .balance(BigDecimal.ZERO)
                .build();
    }

    private TransactionSchedule transactionSchedule(User user, OffsetDateTime nextRunAt, ScheduleState state) {
        Category category = new Category();
        category.setId(100L);
        category.setUser(user);
        category.setType(TransactionType.SPENT);
        category.setName("Servicios");

        TransactionSchedule schedule = new TransactionSchedule();
        schedule.setId(55L);
        schedule.setUser(user);
        schedule.setWallet(wallet(user, "PEN"));
        schedule.setCategory(category);
        schedule.setAmount(new BigDecimal("50.00"));
        schedule.setPaymentMethod(PaymentMethod.CASH);
        schedule.setTransactionType(TransactionType.SPENT);
        schedule.setNextRunAt(nextRunAt);
        schedule.setActive(true);
        schedule.setState(state);
        schedule.setExecutionMode(ExecutionMode.MANUAL);
        return schedule;
    }

    private BudgetSchedule budgetSchedule(User user, OffsetDateTime nextRunAt, ScheduleState state) {
        Category category = new Category();
        category.setId(200L);
        category.setUser(user);
        category.setType(TransactionType.SPENT);
        category.setName("Hogar");

        BudgetSchedule schedule = new BudgetSchedule();
        schedule.setId(77L);
        schedule.setUser(user);
        schedule.setCategory(category);
        schedule.setLimitAmount(new BigDecimal("800.00"));
        schedule.setDescription("Presupuesto mensual");
        schedule.setNextRunAt(nextRunAt);
        schedule.setActive(true);
        schedule.setState(state);
        schedule.setExecutionMode(ExecutionMode.AUTO);
        return schedule;
    }
}
