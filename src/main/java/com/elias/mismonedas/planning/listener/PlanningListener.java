package com.elias.mismonedas.planning.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionDisabledEvent;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.planning.service.PlanningRecheckService;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanningListener {

    private final BudgetRepository budgetRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final PlanningRecheckService planningRecheckService;

    @EventListener
    public void on(UserDeletedEvent event) {
        budgetRepository.deleteAllByUser_Id(event.userId());
        savingGoalRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted budgets and saving goals for user {}", event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TransactionDisabledEvent event) {
        planningRecheckService.requestBudgetRecheck(event.userId());
        planningRecheckService.requestSavingGoalRecheck(event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TransactionRegisteredEvent event) {
        boolean affectsBudgets = event.type() == TransactionType.SPENT && event.categoryId() != null;
        boolean affectsSavingGoals = event.savingGoalId() != null;

        if (affectsBudgets) {
            planningRecheckService.requestBudgetRecheck(event.userId());
        }
        if (affectsSavingGoals) {
            planningRecheckService.requestSavingGoalRecheck(event.userId());
        }
    }
}
