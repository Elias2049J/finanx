package com.elias.mismonedas.planning.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.ledger.domain.event.TransactionChangedEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.planning.service.SavingGoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** F3/F4/F5: planning reacciona a los eventos de ciclo de vida y de transaccion de {@code ledger}. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanningListener {

    private final BudgetRepository budgetRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final BudgetService budgetService;
    private final SavingGoalService savingGoalService;

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        budgetRepository.deleteAllByUser_Id(event.userId());
        savingGoalRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted budgets and saving goals for user {}", event.userId());
    }

    /** F5: recalculo sincrono, dentro de la misma transaccion que escribio la transaccion. */
    @EventListener
    public void onTransactionChanged(TransactionChangedEvent event) {
        budgetService.checkAllBudgets(event.userId());
        savingGoalService.checkAllSavingGoals(event.userId());
    }
}
