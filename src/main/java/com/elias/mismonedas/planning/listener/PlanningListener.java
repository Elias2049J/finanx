package com.elias.mismonedas.planning.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.ledger.domain.event.TransactionDisabledEvent;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.planning.service.SavingGoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanningListener {

    private final BudgetRepository budgetRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final BudgetService budgetService;
    private final SavingGoalService savingGoalService;

    @EventListener
    public void on(UserDeletedEvent event) {
        budgetRepository.deleteAllByUser_Id(event.userId());
        savingGoalRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted budgets and saving goals for user {}", event.userId());
    }

    @EventListener
    public void on(TransactionDisabledEvent event) {
        budgetService.checkAllBudgets(event.userId());
        savingGoalService.checkAllSavingGoals(event.userId());
    }

    @EventListener
    public void on(TransactionRegisteredEvent event) {
        budgetService.checkAllBudgets(event.userId());
        savingGoalService.checkAllSavingGoals(event.userId());
    }
}
