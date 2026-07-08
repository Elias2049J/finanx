package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.analytics.component.TimeBoundList;
import com.elias.mismonedas.dto.date.PeriodRequest;
import com.elias.mismonedas.dto.budget.BudgetRequest;
import com.elias.mismonedas.dto.budget.BudgetResponse;
import com.elias.mismonedas.entity.enums.BudgetHealth;
import com.elias.mismonedas.entity.enums.BudgetState;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BudgetService {
    BudgetResponse create(BudgetRequest request);
    BudgetResponse update(Long id, BudgetRequest request);
    BudgetResponse findById(Long id);
    List<BudgetResponse> findAllByUser(Long idUser);

    List<BudgetResponse> findAllActiveByUserAndState(Long idUser, BudgetState state);

    void cancel(Long id);
    void disable(Long id );

    void checkAllBudgets(Long userId);

    @Transactional(readOnly = true)
    TimeBoundList<BudgetResponse> getBudgetsByHealth(PeriodRequest request, BudgetHealth health);
}

