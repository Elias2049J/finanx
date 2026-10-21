package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.wallets.service.WalletService;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.service.CategoryService;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.mapper.TransactionMapper;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.service.TransactionService;
import com.elias.mismonedas.ledger.service.impl.TransactionServiceImpl;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.planning.service.SavingGoalService;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.mapper.ScheduleMapper;
import com.elias.mismonedas.scheduling.repository.BudgetScheduleRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import com.elias.mismonedas.scheduling.repository.TransactionScheduleRepository;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import jakarta.persistence.EntityManager;
import org.apache.http.HttpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceImplBatchTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private TransactionScheduleRepository tsRepository;
    @Mock private BudgetScheduleRepository bsRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ScheduleMapper tsMapper;
    @Mock private BudgetRepository budgetRepository;
    @Mock private RecurrenceService rcService;
    @Mock private ReasonResolver reasonResolver;
    @Mock private RecurrenceService recurrenceService;
    @Mock private BudgetService budgetService;
    @Mock private SavingGoalService savingGoalService;
    @Mock private WalletService walletService;
    @Mock private TransactionService transactionService;
    @Mock private EntityManager entityManager;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SecurityService securityService;

    @InjectMocks private ScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void shouldQueryOnePageAndStopWhenBatchIsPartialForTransactions() {
        when(tsRepository.findByActiveTrueAndStateAndIdGreaterThan(any(), any(), any()))
                .thenReturn(List.of());

        service.runDueTransactions();

        verify(tsRepository, times(1))
                .findByActiveTrueAndStateAndIdGreaterThan(
                        eq(ScheduleState.RUNNING), eq(0L), any(Pageable.class));
    }

    @Test
    void shouldQueryOnePageAndStopWhenBatchIsPartialForBudgets() {
        when(bsRepository.findByActiveTrueAndStateAndIdGreaterThan(any(), any(), any()))
                .thenReturn(List.of());

        service.runDueBudgets();

        verify(bsRepository, times(1))
                .findByActiveTrueAndStateAndIdGreaterThan(
                        eq(ScheduleState.RUNNING),
                        eq(0L),
                        any(Pageable.class));
    }

    /**
     * Drives the scheduler batch loop with a REAL {@link TransactionServiceImpl} instead of a mock,
     * so the whole security-context-free path is exercised: ScheduleServiceImpl -> TransactionService.create(userId, scheduleId, ...).
     */
    @Nested
    class RealTransactionServiceSchedulerTest {

        @Mock private TransactionRepository transactionRepository;
        @Mock private CategoryService categoryService;
        @Mock private ReceiptRepository receiptRepository;
        @Mock private TransactionMapper transactionMapper;
        @Mock private CurrencyConverter currencyConverter;
        @Mock private SecurityService transactionSecurityService;

        @Test
        void schedulerPathDoesNotTouchSecurityContextAndPropagatesScheduleId() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setPreferences(UserPreferences.builder().timeZone(TimeZone.AMERICA_LIMA).build());

            Wallet wallet = Wallet.builder()
                    .id(10L)
                    .user(user)
                    .currencyCode("PEN")
                    .balance(BigDecimal.ZERO)
                    .build();

            Category category = new Category();
            category.setId(100L);
            category.setType(TransactionType.SPENT);
            category.setUser(user);

            TransactionSchedule schedule = new TransactionSchedule();
            schedule.setId(55L);
            schedule.setUser(user);
            schedule.setCategory(category);
            schedule.setWallet(wallet);
            schedule.setAmount(new BigDecimal("50.00"));
            schedule.setPaymentMethod(PaymentMethod.CASH);
            schedule.setTransactionType(TransactionType.SPENT);
            schedule.setNextRunAt(OffsetDateTime.now(user.getTimeZone().toZoneId()).minusMinutes(1));
            schedule.setActive(true);
            schedule.setState(ScheduleState.RUNNING);

            TransactionServiceImpl realTransactionService = new TransactionServiceImpl(
                    transactionRepository, walletService, categoryService, savingGoalService,
                    receiptRepository, transactionMapper, reasonResolver, currencyConverter,
                    transactionSecurityService, eventPublisher);

            ScheduleServiceImpl schedulerService = new ScheduleServiceImpl(
                    scheduleRepository, tsRepository, bsRepository, categoryRepository, tsMapper,
                    budgetRepository, rcService, reasonResolver, recurrenceService, eventPublisher,
                    realTransactionService, entityManager, walletService, transactionTemplate,
                    securityService);

            when(tsRepository.findByActiveTrueAndStateAndIdGreaterThan(any(), any(), any()))
                    .thenReturn(List.of(schedule));
            when(tsRepository.findById(55L)).thenReturn(Optional.of(schedule));
            when(rcService.computeNextRunAt(schedule)).thenReturn(Optional.empty());

            when(walletService.getOwned(10L, 1L)).thenReturn(wallet);
            when(categoryService.getOwned(100L, 1L)).thenReturn(category);
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.empty());
            when(transactionMapper.toEntity(any())).thenReturn(new Transaction());
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(new TransactionResponse());

            schedulerService.runDueTransactions();

            ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(saved.capture());
            assertThat(saved.getValue().getScheduleId()).isEqualTo(55L);

            ArgumentCaptor<TransactionRegisteredEvent> event =
                    ArgumentCaptor.forClass(TransactionRegisteredEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue().scheduleId()).isEqualTo(55L);

            verify(transactionSecurityService, never()).getCurrentUser();
            verify(transactionService, never()).create(any(), any(), any(), any());
        }
    }
}
