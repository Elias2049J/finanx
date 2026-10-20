package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.wallets.service.WalletService;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.service.TransactionRecorder;
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
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private TransactionRecorder transactionRecorder;
    @Mock private EntityManager entityManager;
    @Mock private TransactionTemplate transactionTemplate;

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
}
