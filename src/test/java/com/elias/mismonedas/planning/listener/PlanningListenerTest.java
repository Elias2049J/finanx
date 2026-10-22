package com.elias.mismonedas.planning.listener;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionDisabledEvent;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import com.elias.mismonedas.planning.service.PlanningRecheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlanningListenerTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private SavingGoalRepository savingGoalRepository;
    @Mock private PlanningRecheckService planningRecheckService;

    private PlanningListener listener;

    @BeforeEach
    void setUp() {
        listener = new PlanningListener(budgetRepository, savingGoalRepository, planningRecheckService);
    }

    private static TransactionRegisteredEvent registered(
            TransactionType type, Long categoryId, Long savingGoalId) {
        return new TransactionRegisteredEvent(
                10L, new BigDecimal("50.00"), 999L, 1L, type, null, categoryId, savingGoalId);
    }

    @Nested
    class OnTransactionRegisteredTest {

        @Test
        @DisplayName("recalcula presupuestos con un gasto que tiene categoria")
        void shouldRecheckBudgetsWhenSpentWithCategory() {
            listener.on(registered(TransactionType.SPENT, 100L, null));

            verify(planningRecheckService).requestBudgetRecheck(1L);
        }

        @Test
        @DisplayName("no recalcula presupuestos si el movimiento es un ingreso")
        void shouldNotRecheckBudgetsOnIncome() {
            listener.on(registered(TransactionType.INCOME, 100L, null));

            verify(planningRecheckService, never()).requestBudgetRecheck(anyLong());
        }

        @Test
        @DisplayName("no recalcula presupuestos si el gasto no tiene categoria")
        void shouldNotRecheckBudgetsWithoutCategory() {
            listener.on(registered(TransactionType.SPENT, null, null));

            verify(planningRecheckService, never()).requestBudgetRecheck(anyLong());
        }

        @Test
        @DisplayName("recalcula objetivos de ahorro cuando la transaccion tiene meta")
        void shouldRecheckSavingGoalsWhenSavingGoalPresent() {
            listener.on(registered(TransactionType.SPENT, 100L, 200L));

            verify(planningRecheckService).requestSavingGoalRecheck(1L);
        }

        @Test
        @DisplayName("no recalcula objetivos de ahorro si no hay meta")
        void shouldNotRecheckSavingGoalsWithoutSavingGoal() {
            listener.on(registered(TransactionType.SPENT, 100L, null));

            verify(planningRecheckService, never()).requestSavingGoalRecheck(anyLong());
        }

        @Test
        @DisplayName("una meta de ahorro dispara tambien el recalculo de presupuestos si es un gasto")
        void shouldRecheckBothWhenSpentWithCategoryAndSavingGoal() {
            listener.on(registered(TransactionType.SPENT, 100L, 200L));

            verify(planningRecheckService).requestBudgetRecheck(1L);
            verify(planningRecheckService).requestSavingGoalRecheck(1L);
        }
    }

    @Nested
    class OnTransactionDisabledTest {

        @Test
        @DisplayName("recalcula presupuestos y objetivos al desactivar un movimiento")
        void shouldRecheckBothPlans() {
            listener.on(new TransactionDisabledEvent(
                    999L, 1L, 10L, new BigDecimal("50.00"), TransactionType.SPENT));

            verify(planningRecheckService).requestBudgetRecheck(1L);
            verify(planningRecheckService).requestSavingGoalRecheck(1L);
        }
    }
}
