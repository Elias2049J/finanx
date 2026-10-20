package com.elias.mismonedas.planning.listener;

import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.planning.service.SavingGoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetRecheckListener {

    private final BudgetService budgetService;
    private final SavingGoalService savingGoalService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionRegistered(TransactionRegisteredEvent event) {
        try {
            budgetService.checkAllBudgets(event.userId());
        } catch (RuntimeException ex) {
            log.error("Recomputo de presupuestos fallo para el usuario {} tras crear la transaccion {}",
                    event.userId(), event.transactionId(), ex);
        }
        try {
            savingGoalService.checkAllSavingGoals(event.userId());
        } catch (RuntimeException ex) {
            log.error("Recomputo de metas fallo para el usuario {} tras crear la transaccion {}",
                    event.userId(), event.transactionId(), ex);
        }
    }
}
