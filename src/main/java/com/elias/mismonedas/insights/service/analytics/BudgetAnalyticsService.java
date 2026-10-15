package com.elias.mismonedas.insights.service.analytics;

import com.elias.mismonedas.insights.service.analytics.AnalyticsService;
import com.elias.mismonedas.planning.dto.budget.BudgetExecutionResponse;
import com.elias.mismonedas.shared.dto.date.PeriodRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BudgetAnalyticsService extends AnalyticsService{
    BudgetExecutionResponse getBudgetExecution(Long id);

    @Transactional(readOnly = true)
    List<BudgetExecutionResponse> getBudgetsExecutions(PeriodRequest request, Long userId);
}
