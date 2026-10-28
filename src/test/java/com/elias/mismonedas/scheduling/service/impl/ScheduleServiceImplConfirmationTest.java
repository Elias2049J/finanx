package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.service.TransactionService;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.ExecutionMode;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceConfirmationResponse;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceKeyRequest;
import com.elias.mismonedas.scheduling.dto.schedule.ScheduleResponse;
import com.elias.mismonedas.scheduling.dto.schedule.TransactionScheduleResponse;
import com.elias.mismonedas.scheduling.mapper.ScheduleMapper;
import com.elias.mismonedas.scheduling.repository.BudgetScheduleRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import com.elias.mismonedas.scheduling.repository.TransactionScheduleRepository;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.shared.exception.ConflictException;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import com.elias.mismonedas.wallets.service.WalletService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceImplConfirmationTest {

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

    @BeforeEach
    void setUp() {
        service = new ScheduleServiceImpl(
                scheduleRepository, tsRepository, bsRepository, categoryRepository, tsMapper,
                budgetRepository, rcService, reasonResolver, recurrenceService, eventPublisher,
                transactionService, transactionRepository, entityManager, walletService, walletRepository,
                transactionTemplate, securityService);
    }

    @Test
    void confirmMaterializesTransactionAndAdvancesOccurrence() {
        User user = user();
        OffsetDateTime pendingAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).minusHours(2);
        OffsetDateTime next = pendingAt.plusMonths(1);
        TransactionSchedule schedule = transactionSchedule(user, pendingAt, ScheduleState.PENDING_CONFIRMATION);
        String key = occurrenceKey(55L, pendingAt);

        TransactionResponse created = new TransactionResponse();
        created.setId(42L);

        when(scheduleRepository.findById(55L)).thenReturn(Optional.of(schedule));
        when(securityService.getCurrentUser()).thenReturn(user);
        when(scheduleRepository.claimPendingConfirmation(55L, pendingAt)).thenReturn(1);
        when(transactionService.create(anyLong(), anyLong(), any(), anyString())).thenReturn(created);
        when(rcService.computeNextRunAt(eq(schedule), any())).thenReturn(Optional.of(next));

        OccurrenceConfirmationResponse response = service.confirm(55L, new OccurrenceKeyRequest(key));

        assertThat(response.getOccurrenceKey()).isEqualTo(key);
        assertThat(response.getTransactionId()).isEqualTo(42L);
        assertThat(response.getBudgetId()).isNull();
        assertThat(schedule.getNextRunAt()).isEqualTo(next);
        assertThat(schedule.getState()).isEqualTo(ScheduleState.RUNNING);
        assertThat(schedule.getLastRunAt()).isNotNull();
        verify(scheduleRepository).save(schedule);
        verify(transactionService).create(eq(1L), eq(55L), any(), eq(key));
    }

    @Test
    void confirmReplaysAlreadyMaterializedOccurrence() {
        User user = user();
        OffsetDateTime pendingAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).minusHours(2);
        TransactionSchedule schedule = transactionSchedule(user, pendingAt.plusMonths(1), ScheduleState.RUNNING);
        String key = occurrenceKey(55L, pendingAt);

        Transaction existing = new Transaction();
        existing.setId(42L);

        when(scheduleRepository.findById(55L)).thenReturn(Optional.of(schedule));
        when(securityService.getCurrentUser()).thenReturn(user);
        when(transactionRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        OccurrenceConfirmationResponse response = service.confirm(55L, new OccurrenceKeyRequest(key));

        assertThat(response.getTransactionId()).isEqualTo(42L);
        verify(transactionService, never()).create(any(), any(), any(), any());
        verify(scheduleRepository, never()).claimPendingConfirmation(any(), any());
    }

    @Test
    void confirmRejectsStaleOccurrenceKey() {
        User user = user();
        OffsetDateTime pendingAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).minusHours(2);
        TransactionSchedule schedule = transactionSchedule(user, pendingAt, ScheduleState.PENDING_CONFIRMATION);

        when(scheduleRepository.findById(55L)).thenReturn(Optional.of(schedule));
        when(securityService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> service.confirm(55L, new OccurrenceKeyRequest("schedule:55:1")))
                .isInstanceOf(ConflictException.class);
        verify(transactionService, never()).create(any(), any(), any(), any());
    }

    @Test
    void confirmRejectsKeyFromAnotherScheduleWithoutLeakingItsTransaction() {
        User user = user();
        OffsetDateTime pendingAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).minusHours(2);
        TransactionSchedule schedule = transactionSchedule(user, pendingAt, ScheduleState.PENDING_CONFIRMATION);

        when(scheduleRepository.findById(55L)).thenReturn(Optional.of(schedule));
        when(securityService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> service.confirm(55L, new OccurrenceKeyRequest("schedule:999:1")))
                .isInstanceOf(ConflictException.class);
        verify(transactionRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    void confirmRejectsScheduleOwnedByAnotherUser() {
        User owner = user();
        User other = new User();
        other.setId(2L);
        other.setPreferences(UserPreferences.builder().timeZone(TimeZone.AMERICA_LIMA).build());
        TransactionSchedule schedule = transactionSchedule(owner, OffsetDateTime.now().minusHours(1),
                ScheduleState.PENDING_CONFIRMATION);

        when(scheduleRepository.findById(55L)).thenReturn(Optional.of(schedule));
        when(securityService.getCurrentUser()).thenReturn(other);

        assertThatThrownBy(() -> service.confirm(55L, new OccurrenceKeyRequest(occurrenceKey(55L, schedule.getNextRunAt()))))
                .isInstanceOf(AccessDeniedException.class);
        verify(scheduleRepository, never()).claimPendingConfirmation(any(), any());
    }

    @Test
    void ignoreAdvancesOccurrenceWithoutMaterializing() {
        User user = user();
        OffsetDateTime pendingAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).minusHours(2);
        OffsetDateTime next = pendingAt.plusMonths(1);
        TransactionSchedule schedule = transactionSchedule(user, pendingAt, ScheduleState.PENDING_CONFIRMATION);

        when(scheduleRepository.findById(55L)).thenReturn(Optional.of(schedule));
        when(securityService.getCurrentUser()).thenReturn(user);
        when(scheduleRepository.claimPendingConfirmation(55L, pendingAt)).thenReturn(1);
        when(rcService.computeNextRunAt(eq(schedule), any())).thenReturn(Optional.of(next));

        service.ignore(55L, new OccurrenceKeyRequest(occurrenceKey(55L, pendingAt)));

        assertThat(schedule.getNextRunAt()).isEqualTo(next);
        assertThat(schedule.getState()).isEqualTo(ScheduleState.RUNNING);
        verify(scheduleRepository).save(schedule);
        verify(transactionService, never()).create(any(), any(), any(), any());
    }

    @Test
    void ignoreRejectsOccurrenceAlreadyClaimed() {
        User user = user();
        OffsetDateTime pendingAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).minusHours(2);
        TransactionSchedule schedule = transactionSchedule(user, pendingAt, ScheduleState.PENDING_CONFIRMATION);

        when(scheduleRepository.findById(55L)).thenReturn(Optional.of(schedule));
        when(securityService.getCurrentUser()).thenReturn(user);
        when(scheduleRepository.claimPendingConfirmation(55L, pendingAt)).thenReturn(0);

        assertThatThrownBy(() -> service.ignore(55L, new OccurrenceKeyRequest(occurrenceKey(55L, pendingAt))))
                .isInstanceOf(ConflictException.class);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void confirmAnchorsBudgetPeriodToConfirmationDate() {
        User user = user();
        OffsetDateTime pendingAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).minusMonths(2);
        OffsetDateTime next = pendingAt.plusMonths(1);
        String key = occurrenceKey(66L, pendingAt);

        Wallet wallet = Wallet.builder()
                .id(10L)
                .user(user)
                .currencyCode("PEN")
                .balance(BigDecimal.ZERO)
                .build();

        BudgetSchedule schedule = new BudgetSchedule();
        schedule.setId(66L);
        schedule.setUser(user);
        schedule.setLimitAmount(new BigDecimal("100.00"));
        schedule.setNextRunAt(pendingAt);
        schedule.setActive(true);
        schedule.setState(ScheduleState.PENDING_CONFIRMATION);
        schedule.setExecutionMode(ExecutionMode.MANUAL);

        Budget persisted = Budget.builder().id(7L).build();

        when(scheduleRepository.findById(66L)).thenReturn(Optional.of(schedule));
        when(securityService.getCurrentUser()).thenReturn(user);
        when(scheduleRepository.claimPendingConfirmation(66L, pendingAt)).thenReturn(1);
        when(budgetRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(rcService.computeNextRunAt(eq(schedule), any())).thenReturn(Optional.of(next));
        when(entityManager.getReference(User.class, 1L)).thenReturn(user);
        when(walletRepository.findByUserIdAndPrimaryTrue(1L)).thenReturn(Optional.of(wallet));
        when(budgetRepository.save(any(Budget.class))).thenReturn(persisted);

        OffsetDateTime before = OffsetDateTime.now(user.getTimeZone().toZoneId());
        OccurrenceConfirmationResponse response = service.confirm(66L, new OccurrenceKeyRequest(key));
        OffsetDateTime after = OffsetDateTime.now(user.getTimeZone().toZoneId());

        assertThat(response.getBudgetId()).isEqualTo(7L);
        assertThat(response.getTransactionId()).isNull();

        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).save(saved.capture());
        assertThat(saved.getValue().getStart()).isBetween(before, after);
        assertThat(saved.getValue().getEnd()).isEqualTo(next);
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo(key);
        assertThat(schedule.getNextRunAt()).isEqualTo(next);
    }

    @Test
    void confirmAppliesEditedTransactionValues() {
        User user = user();
        OffsetDateTime pendingAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).minusHours(2);
        OffsetDateTime next = pendingAt.plusMonths(1);
        TransactionSchedule schedule = transactionSchedule(user, pendingAt, ScheduleState.PENDING_CONFIRMATION);
        String key = occurrenceKey(55L, pendingAt);

        TransactionResponse created = new TransactionResponse();
        created.setId(42L);

        when(scheduleRepository.findById(55L)).thenReturn(Optional.of(schedule));
        when(securityService.getCurrentUser()).thenReturn(user);
        when(scheduleRepository.claimPendingConfirmation(55L, pendingAt)).thenReturn(1);
        when(transactionService.create(anyLong(), anyLong(), any(), anyString())).thenReturn(created);
        when(rcService.computeNextRunAt(eq(schedule), any())).thenReturn(Optional.of(next));

        OccurrenceKeyRequest request = new OccurrenceKeyRequest();
        request.setOccurrenceKey(key);
        request.setAmount(new BigDecimal("99.90"));
        request.setWalletId(11L);
        request.setCategoryId(101L);
        request.setReasonId(7L);
        request.setDescription("Renta ajustada");
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);

        service.confirm(55L, request);

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).create(eq(1L), eq(55L), captor.capture(), eq(key));

        TransactionRequest sent = captor.getValue();
        assertThat(sent.getAmount()).isEqualByComparingTo("99.90");
        assertThat(sent.getWalletId()).isEqualTo(11L);
        assertThat(sent.getCategoryId()).isEqualTo(101L);
        assertThat(sent.getReasonId()).isEqualTo(7L);
        assertThat(sent.getDescription()).isEqualTo("Renta ajustada");
        assertThat(sent.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(sent.getType()).isEqualTo(TransactionType.SPENT);
    }

    @Test
    void findByIdExposesOccurrenceKeyOnlyWhilePending() {
        User user = user();
        OffsetDateTime pendingAt = OffsetDateTime.now(user.getTimeZone().toZoneId()).minusHours(1);
        TransactionSchedule pending = transactionSchedule(user, pendingAt, ScheduleState.PENDING_CONFIRMATION);
        TransactionScheduleResponse response = new TransactionScheduleResponse();

        when(securityService.getCurrentUser()).thenReturn(user);
        when(scheduleRepository.findByIdAndUser_Id(55L, 1L)).thenReturn(Optional.of(pending));
        when(tsMapper.toResponseDispatch(pending)).thenReturn(response);
        when(walletRepository.findByUserIdAndPrimaryTrue(1L)).thenReturn(Optional.empty());

        ScheduleResponse result = service.findById(55L);

        assertThat(result.getOccurrenceKey()).isEqualTo(occurrenceKey(55L, pendingAt));
    }

    @Test
    void findByIdOmitsOccurrenceKeyWhenOccurrenceIsNotPending() {
        User user = user();
        OffsetDateTime next = OffsetDateTime.now(user.getTimeZone().toZoneId()).plusDays(1);
        TransactionSchedule running = transactionSchedule(user, next, ScheduleState.RUNNING);
        TransactionScheduleResponse response = new TransactionScheduleResponse();

        when(securityService.getCurrentUser()).thenReturn(user);
        when(scheduleRepository.findByIdAndUser_Id(55L, 1L)).thenReturn(Optional.of(running));
        when(tsMapper.toResponseDispatch(running)).thenReturn(response);
        when(walletRepository.findByUserIdAndPrimaryTrue(1L)).thenReturn(Optional.empty());

        assertThat(service.findById(55L).getOccurrenceKey()).isNull();
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setPreferences(UserPreferences.builder().timeZone(TimeZone.AMERICA_LIMA).build());
        return user;
    }

    private TransactionSchedule transactionSchedule(User user, OffsetDateTime nextRunAt, ScheduleState state) {
        Wallet wallet = Wallet.builder()
                .id(10L)
                .user(user)
                .currencyCode("PEN")
                .balance(BigDecimal.ZERO)
                .build();

        Category category = new Category();
        category.setId(100L);
        category.setUser(user);
        category.setType(TransactionType.SPENT);

        TransactionSchedule schedule = new TransactionSchedule();
        schedule.setId(55L);
        schedule.setUser(user);
        schedule.setWallet(wallet);
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

    private String occurrenceKey(Long scheduleId, OffsetDateTime at) {
        return "schedule:" + scheduleId + ":" + at.toInstant().toEpochMilli();
    }
}
