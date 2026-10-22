package com.elias.mismonedas.planning.service.impl;

import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.planning.service.SavingGoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanningRecheckServiceImplTest {

    @Mock private BudgetService budgetService;
    @Mock private SavingGoalService savingGoalService;

    private ManualExecutorService executor;

    private PlanningRecheckServiceImpl service;

    @BeforeEach
    void setUp() {
        executor = new ManualExecutorService();
        service = new PlanningRecheckServiceImpl(budgetService, savingGoalService, executor);
    }

    @Nested
    class BudgetRecheckTest {

        @Test
        @DisplayName("recalcula los presupuestos del usuario")
        void shouldRecheckBudgets() {
            service.requestBudgetRecheck(1L);
            executor.runAll();

            verify(budgetService).checkAllBudgets(1L);
        }

        @Test
        @DisplayName("coalesce peticiones concurrentes del mismo usuario")
        void shouldCoalesceConcurrentRequestsForSameUser() {
            service.requestBudgetRecheck(1L);
            service.requestBudgetRecheck(1L);
            service.requestBudgetRecheck(1L);
            executor.runAll();

            verify(budgetService, times(1)).checkAllBudgets(1L);
        }

        @Test
        @DisplayName("no coalesce entre usuarios distintos")
        void shouldNotCoalesceAcrossUsers() {
            service.requestBudgetRecheck(1L);
            service.requestBudgetRecheck(2L);
            executor.runAll();

            verify(budgetService).checkAllBudgets(1L);
            verify(budgetService).checkAllBudgets(2L);
        }

        @Test
        @DisplayName("libera el marcador de en-vuelo al terminar, allowing a new run")
        void shouldAllowANewRunAfterThePreviousOneFinishes() {
            service.requestBudgetRecheck(1L);
            executor.runAll();
            service.requestBudgetRecheck(1L);
            executor.runAll();

            verify(budgetService, times(2)).checkAllBudgets(1L);
        }

        @Test
        @DisplayName("el fallo de un recalculo no propaga ni deja el marcador colgado")
        void shouldIsolateFailureAndReleaseInFlightMarker() {
            doThrow(new IllegalStateException("boom")).when(budgetService).checkAllBudgets(1L);

            service.requestBudgetRecheck(1L);
            assertThatNoException().isThrownBy(() -> executor.runAll());

            // El marcador se libero pese al fallo: una peticion posterior debe
            // poder volver a ejecutar el recalculo.
            service.requestBudgetRecheck(1L);
            executor.runAll();

            verify(budgetService, times(2)).checkAllBudgets(1L);
        }
    }

    @Nested
    class SavingGoalRecheckTest {

        @Test
        @DisplayName("recalcula los objetivos de ahorro del usuario")
        void shouldRecheckSavingGoals() {
            service.requestSavingGoalRecheck(1L);
            executor.runAll();

            verify(savingGoalService).checkAllSavingGoals(1L);
        }

        @Test
        @DisplayName("coalesce peticiones concurrentes del mismo usuario")
        void shouldCoalesceConcurrentRequestsForSameUser() {
            service.requestSavingGoalRecheck(1L);
            service.requestSavingGoalRecheck(1L);
            executor.runAll();

            verify(savingGoalService, times(1)).checkAllSavingGoals(1L);
        }

        @Test
        @DisplayName("no coalesce con el recalculo de presupuestos")
        void shouldNotCoalesceWithBudgetRecalculation() {
            service.requestBudgetRecheck(1L);
            service.requestSavingGoalRecheck(1L);
            executor.runAll();

            verify(budgetService).checkAllBudgets(1L);
            verify(savingGoalService).checkAllSavingGoals(1L);
        }
    }

    @Nested
    class ResilienceTest {

        @Test
        @DisplayName("ignora peticiones sin usuario")
        void shouldIgnoreNullUserId() {
            service.requestBudgetRecheck(null);
            service.requestSavingGoalRecheck(null);

            assertThatNoException().isThrownBy(() -> executor.runAll());
            assertThat(executor.pendingCount()).isZero();
            verify(budgetService, never()).checkAllBudgets(anyLong());
            verify(savingGoalService, never()).checkAllSavingGoals(anyLong());
        }

        @Test
        @DisplayName("absorbe el rechazo del executor saturado y libera el marcador")
        void shouldAbsorbRejectionAndReleaseInFlightMarker() {
            ExecutorService rejecting = mock(ExecutorService.class);
            doThrow(new RejectedExecutionException("saturado"))
                    .when(rejecting).execute(any());
            PlanningRecheckServiceImpl rejectingService =
                    new PlanningRecheckServiceImpl(budgetService, savingGoalService, rejecting);

            assertThatNoException().isThrownBy(() -> rejectingService.requestBudgetRecheck(1L));
            verify(budgetService, never()).checkAllBudgets(anyLong());

            // El marcador se libero: la siguiente peticion vuelve a encolar.
            rejectingService.requestBudgetRecheck(1L);
            verify(rejecting, times(2)).execute(any());
        }
    }

    /**
     * Executor de pruebas: encola las tareas y solo las ejecuta cuando el test
     * lo pide. Permite observar el estado "en vuelo" sin carreras.
     */
    private static final class ManualExecutorService extends AbstractExecutorService {

        private final List<Runnable> pending = new ArrayList<>();

        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.copyOf(pending);
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && pending.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        void runAll() {
            List<Runnable> snapshot = new ArrayList<>(pending);
            pending.clear();
            snapshot.forEach(Runnable::run);
        }

        int pendingCount() {
            return pending.size();
        }
    }
}
