package com.elias.finanx.dto.analytics.dashboard;

import com.elias.finanx.dto.analytics.component.Ranking;
import com.elias.finanx.dto.analytics.component.TimeLine;
import com.elias.finanx.dto.category.CategorySummary;
import com.elias.finanx.dto.reason.ReasonSummary;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionsDashboard extends DashboardResponse {
    private Ranking<CategorySummary> summaryByCategory;
    private Ranking<PaymentMethod> summaryByPaymentMethod;
    private Ranking<TransactionType> summaryByType;

    private TimeLine<TransactionResponse> dailyExpenses;
    private TimeLine<TransactionResponse> dailyIncome;

    private Ranking<CategorySummary> topCategories;
    private Ranking<ReasonSummary> topSpentReasons;
    private Ranking<ReasonSummary> topIncomeReasons;
}
