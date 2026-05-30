package com.elias.finanx.service;

import com.elias.finanx.dto.analytics.*;
import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.RecurrenceType;
import com.elias.finanx.entity.enums.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface AnalyticsService {

    // Dashboards
    AnalyticsDashboard getDashboard(Long userId, AnalyticsPeriod period);


    // --- Transactions ---

    TransactionAnalytics getTransactionOverview(Long userId, AnalyticsPeriod period);

    List<TimeSeriesPoint> getDailyNetSeries(Long userId, AnalyticsPeriod period);

    Map<TransactionType, TransactionAggregate> getTransactionAggregatesByType(Long userId, AnalyticsPeriod period);

    Map<PaymentMethod, TransactionAggregate> getTransactionAggregatesByPaymentMethod(Long userId, AnalyticsPeriod period);

    List<RankingItem<PaymentMethod>> getTopPaymentMethods(Long userId, AnalyticsPeriod period, int topN);

    PaymentMethod getMostUsedPaymentMethod(Long userId, AnalyticsPeriod period);


    // --- Categories ---

    CategoryAnalytics getCategoryOverview(Long userId, AnalyticsPeriod period);

    Map<TransactionType, BigDecimal> getTotalAmountByTransactionType(Long userId, AnalyticsPeriod period);

    List<RankingItem<CategorySummary>> getTopCategories(Long userId, AnalyticsPeriod period, int topN, TransactionType type);

    List<CategoryAggregate> getCategoryAggregates(Long userId, AnalyticsPeriod period);


    // --- Reasons ---

    ReasonAnalytics getReasonOverview(Long userId, AnalyticsPeriod period, int topN);

    List<RankingItem<ReasonSummary>> getTopReasons(Long userId, AnalyticsPeriod period, int topN, TransactionType type);



    // --- Budgets ---

    BudgetAnalytics getBudgetOverview(Long userId, AnalyticsPeriod period);

    List<BudgetAnalytics> getBudgetExecutions(Long userId, AnalyticsPeriod period);

    List<BudgetAnalytics> getExceededBudgets(Long userId, AnalyticsPeriod period);

    List<BudgetAnalytics> getNearLimitBudgets(Long userId, AnalyticsPeriod period);


    // --- Scheduled / Projections ---

    TransactionScheduleAnalytics getScheduledOverview(Long userId, OffsetDateTime projectionStart, OffsetDateTime projectionEnd);

    TransactionScheduleAnalytics makeProjection(Long userId, OffsetDateTime projectionStart, OffsetDateTime projectionEnd, TransactionType type);

    long getActiveScheduledTransactionCount(Long userId);

    Map<RecurrenceType, Long> getActiveScheduledCountByRecurrenceType(Long userId);

    List<ScheduledProjection> getScheduledProjections(Long userId, OffsetDateTime projectionStart, OffsetDateTime projectionEnd);

    List<Insight> getUserInsights(Long userId, AnalyticsPeriod period);

    List<Insight> getTransactionInsights(Long userId, AnalyticsPeriod period);

    List<Insight> getBudgetInsights(Long userId, AnalyticsPeriod period);

    List<Insight> getScheduledInsights(Long userId, OffsetDateTime projectionStart, OffsetDateTime projectionEnd);
}
