package com.elias.finanx.service.analytics;

import com.elias.finanx.dto.budget.BudgetExecutionResponse;

public interface BudgetAnalyticsService extends AnalyticsService{
    BudgetExecutionResponse getBudgetExecution(Long id);
}
