package com.elias.mismonedas.planning.service.impl;

import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.planning.service.PlanningRecheckService;
import com.elias.mismonedas.planning.service.SavingGoalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class PlanningRecheckServiceImpl implements PlanningRecheckService {

    private final BudgetService budgetService;
    private final SavingGoalService savingGoalService;
    private final ExecutorService executor;

    private final Set<Long> budgetInFlight = ConcurrentHashMap.newKeySet();

    private final Set<Long> savingGoalInFlight = ConcurrentHashMap.newKeySet();

    public PlanningRecheckServiceImpl(
            BudgetService budgetService,
            SavingGoalService savingGoalService,
            @Qualifier("planningRecheckExecutor") ExecutorService executor) {
        this.budgetService = budgetService;
        this.savingGoalService = savingGoalService;
        this.executor = executor;
    }

    @Override
    public void requestBudgetRecheck(Long userId) {
        schedule(userId, budgetInFlight, "budget", () -> budgetService.checkAllBudgets(userId));
    }

    @Override
    public void requestSavingGoalRecheck(Long userId) {
        schedule(userId, savingGoalInFlight, "savingGoal", () -> savingGoalService.checkAllSavingGoals(userId));
    }

    private void schedule(Long userId, Set<Long> inFlight, String kind, Runnable task) {
        if (userId == null) {
            return;
        }
        if (!inFlight.add(userId)) {
            log.debug("Recalculo de {} ya en curso para el usuario {}; peticion coalescida", kind, userId);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("Fallo el recalculo de {} para el usuario {}", kind, userId, e);
                } finally {
                    inFlight.remove(userId);
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(userId);
            log.warn("Recalculo de {} rechazado para el usuario {} (executor saturado)", kind, userId);
        }
    }
}
