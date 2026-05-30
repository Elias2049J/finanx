package com.elias.finanx.service.impl;

import com.elias.finanx.dto.analytics.*;
import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.RecurrenceType;
import com.elias.finanx.entity.enums.TransactionType;
import com.elias.finanx.service.AnalyticsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
@Service
public class AnalyticsServiceImpl implements AnalyticsService {
    @Override
    public AnalyticsDashboard getDashboard(Long userId, AnalyticsPeriod period) {
        return null;
    }

    @Override
    public TransactionAnalytics getTransactionOverview(Long userId, AnalyticsPeriod period) {
        return null;
    }

    @Override
    public List<TimeSeriesPoint> getDailyNetSeries(Long userId, AnalyticsPeriod period) {
        return List.of();
    }

    @Override
    public Map<TransactionType, TransactionAggregate> getTransactionAggregatesByType(Long userId, AnalyticsPeriod period) {
        return Map.of();
    }

    @Override
    public Map<PaymentMethod, TransactionAggregate> getTransactionAggregatesByPaymentMethod(Long userId, AnalyticsPeriod period) {
        return Map.of();
    }

    @Override
    public List<RankingItem<PaymentMethod>> getTopPaymentMethods(Long userId, AnalyticsPeriod period, int topN) {
        return List.of();
    }

    @Override
    public PaymentMethod getMostUsedPaymentMethod(Long userId, AnalyticsPeriod period) {
        return null;
    }

    @Override
    public CategoryAnalytics getCategoryOverview(Long userId, AnalyticsPeriod period) {
        return null;
    }

    @Override
    public Map<TransactionType, BigDecimal> getTotalAmountByTransactionType(Long userId, AnalyticsPeriod period) {
        return Map.of();
    }

    @Override
    public List<RankingItem<CategorySummary>> getTopCategories(Long userId, AnalyticsPeriod period, int topN, TransactionType type) {
        return List.of();
    }

    @Override
    public List<CategoryAggregate> getCategoryAggregates(Long userId, AnalyticsPeriod period) {
        return List.of();
    }

    @Override
    public ReasonAnalytics getReasonOverview(Long userId, AnalyticsPeriod period, int topN) {
        return null;
    }

    @Override
    public List<RankingItem<ReasonSummary>> getTopReasons(Long userId, AnalyticsPeriod period, int topN, TransactionType type) {
        return List.of();
    }

    @Override
    public BudgetAnalytics getBudgetOverview(Long userId, AnalyticsPeriod period) {
        return null;
    }

    @Override
    public List<BudgetAnalytics> getBudgetExecutions(Long userId, AnalyticsPeriod period) {
        return List.of();
    }

    @Override
    public List<BudgetAnalytics> getExceededBudgets(Long userId, AnalyticsPeriod period) {
        return List.of();
    }

    @Override
    public List<BudgetAnalytics> getNearLimitBudgets(Long userId, AnalyticsPeriod period) {
        return List.of();
    }

    @Override
    public TransactionScheduleAnalytics getScheduledOverview(Long userId, OffsetDateTime projectionStart, OffsetDateTime projectionEnd) {
        return null;
    }

    @Override
    public TransactionScheduleAnalytics makeProjection(Long userId, OffsetDateTime projectionStart, OffsetDateTime projectionEnd, TransactionType type) {
        return null;
    }

    @Override
    public long getActiveScheduledTransactionCount(Long userId) {
        return 0;
    }

    @Override
    public Map<RecurrenceType, Long> getActiveScheduledCountByRecurrenceType(Long userId) {
        return Map.of();
    }

    @Override
    public List<ScheduledProjection> getScheduledProjections(Long userId, OffsetDateTime projectionStart, OffsetDateTime projectionEnd) {
        return List.of();
    }


    @Override
    public List<Insight> getUserInsights(Long userId, AnalyticsPeriod period) {
        return List.of();
    }

    @Override
    public List<Insight> getTransactionInsights(Long userId, AnalyticsPeriod period) {
        return List.of();
    }

    @Override
    public List<Insight> getBudgetInsights(Long userId, AnalyticsPeriod period) {
        return List.of();
    }

    @Override
    public List<Insight> getScheduledInsights(Long userId, OffsetDateTime projectionStart, OffsetDateTime projectionEnd) {
        return List.of();
    }
}
