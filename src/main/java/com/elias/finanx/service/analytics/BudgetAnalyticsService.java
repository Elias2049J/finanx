package com.elias.finanx.service.analytics;

import com.elias.finanx.dto.budget.BudgetExecutionResponse;
import com.elias.finanx.dto.date.PeriodRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BudgetAnalyticsService extends AnalyticsService{
    BudgetExecutionResponse getBudgetExecution(Long id);

    @Transactional(readOnly = true)
    List<BudgetExecutionResponse> getBudgetsExecutions(PeriodRequest request);
}
