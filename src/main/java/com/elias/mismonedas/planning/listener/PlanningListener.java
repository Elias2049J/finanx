package com.elias.mismonedas.planning.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** F3/F4: planning borra presupuestos y metas de ahorro cuando se elimina una cuenta. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanningListener {

    private final BudgetRepository budgetRepository;
    private final SavingGoalRepository savingGoalRepository;

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        budgetRepository.deleteAllByUser_Id(event.userId());
        savingGoalRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted budgets and saving goals for user {}", event.userId());
    }
}
