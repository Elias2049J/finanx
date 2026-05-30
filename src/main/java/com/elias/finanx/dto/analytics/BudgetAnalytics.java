package com.elias.finanx.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetAnalytics {
    private Long userId;
    private AnalyticsPeriod period;

    private BigDecimal totalBudgetLimitAmount;
    private BigDecimal totalBudgetSpentAmount;
    private BigDecimal totalBudgetRemainingAmount;

    private List<BudgetExecution> budgetExecutions;
    private List<Insight> insights;



    public enum BudgetHealth {
        OK,
        NEAR_LIMIT,
        EXCEEDED,
        INACTIVE
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryDTO {
        private Long categoryId;
        private String categoryName;
    }
}
