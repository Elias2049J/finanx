package com.elias.finanx.dto.analytics.dashboard;

import com.elias.finanx.dto.analytics.TimeBoundList;
import com.elias.finanx.dto.analytics.component.AggregateBy;
import com.elias.finanx.dto.analytics.component.Ranking;
import com.elias.finanx.dto.analytics.component.TimeLine;
import com.elias.finanx.dto.budget.BudgetExecutionResponse;
import com.elias.finanx.dto.budget.BudgetResponse;
import com.elias.finanx.dto.category.CategoryResponse;
import com.elias.finanx.entity.enums.BudgetHealth;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BudgetsDashboard extends DashboardResponse {
    private List<AggregateBy<BudgetHealth>> byHealth;
    private List<AggregateBy<BudgetExecutionResponse>> byCategory;

    private TimeLine<BudgetExecutionResponse> executionEvolution;

    private Ranking<CategoryResponse> mostExecuted;
    private Ranking<CategoryResponse> mostRemaining;

    private BigDecimal totalBudgetLimitAmount;
    private BigDecimal totalBudgetSpentAmount;
    private BigDecimal totalBudgetRemainingAmount;
}