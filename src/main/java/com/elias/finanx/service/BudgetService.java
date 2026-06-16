package com.elias.finanx.service;

import com.elias.finanx.dto.analytics.TimeBoundList;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.finanx.dto.budget.BudgetExecutionResponse;
import com.elias.finanx.dto.budget.BudgetRequest;
import com.elias.finanx.dto.budget.BudgetResponse;
import com.elias.finanx.entity.enums.BudgetHealth;
import com.elias.finanx.entity.enums.BudgetState;
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

