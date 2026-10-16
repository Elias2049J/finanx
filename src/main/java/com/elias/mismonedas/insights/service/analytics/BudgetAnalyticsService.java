package com.elias.mismonedas.insights.service.analytics;

import com.elias.mismonedas.insights.service.analytics.AnalyticsService;
import com.elias.mismonedas.planning.dto.budget.BudgetExecutionResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface BudgetAnalyticsService extends AnalyticsService{
    BudgetExecutionResponse getBudgetExecution(Long id);

    PageResponse<BudgetExecutionResponse> getBudgetsExecutions(
            LocalDate from, LocalDate to, Pageable pageable);
}
