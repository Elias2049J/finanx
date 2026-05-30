package com.elias.finanx.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDashboard {
    private Long userId;
    private AnalyticsPeriod period;

    private TransactionAnalytics transactions;
    private CategoryAnalytics categories;
    private BudgetAnalytics budgets;
    private ReasonAnalytics reasons;
    private TransactionScheduleAnalytics scheduled;

    private List<Insight> insights;
}
