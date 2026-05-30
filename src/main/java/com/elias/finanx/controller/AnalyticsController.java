package com.elias.finanx.controller;

import com.elias.finanx.dto.analytics.*;
import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.RecurrenceType;
import com.elias.finanx.entity.enums.TransactionType;
import com.elias.finanx.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;


    @GetMapping("/dashboard/{userId}")
    public AnalyticsDashboard dashboard(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end
    ) {
        return analyticsService.getDashboard(userId, new AnalyticsPeriod(start, end));
    }

    // --- Transactions ---
    @PostMapping("/transactions/overview")
    public ResponseEntity<TransactionAnalytics> getTransactionOverview(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getTransactionOverview(userId, period));
    }

    @PostMapping("/transactions/daily-net-series")
    public ResponseEntity<List<TimeSeriesPoint>> getDailyNetSeries(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getDailyNetSeries(userId, period));
    }

    @PostMapping("/transactions/aggregates/type")
    public ResponseEntity<Map<TransactionType, TransactionAggregate>> getTransactionAggregatesByType(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getTransactionAggregatesByType(userId, period));
    }

    @PostMapping("/transactions/aggregates/payment-method")
    public ResponseEntity<Map<PaymentMethod, TransactionAggregate>> getTransactionAggregatesByPaymentMethod(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getTransactionAggregatesByPaymentMethod(userId, period));
    }

    @PostMapping("/transactions/top-payment-methods")
    public ResponseEntity<List<RankingItem<PaymentMethod>>> getTopPaymentMethods(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period,
            @RequestParam int topN) {
        return ResponseEntity.ok(analyticsService.getTopPaymentMethods(userId, period, topN));
    }

    @PostMapping("/transactions/most-used-payment-method")
    public ResponseEntity<PaymentMethod> getMostUsedPaymentMethod(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getMostUsedPaymentMethod(userId, period));
    }

    // --- Categories ---
    @PostMapping("/categories/overview")
    public ResponseEntity<CategoryAnalytics> getCategoryOverview(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getCategoryOverview(userId, period));
    }

    @PostMapping("/categories/total-amount-by-type")
    public ResponseEntity<Map<TransactionType, BigDecimal>> getTotalAmountByTransactionType(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getTotalAmountByTransactionType(userId, period));
    }

    @PostMapping("/categories/top")
    public ResponseEntity<List<RankingItem<CategorySummary>>> getTopCategories(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period,
            @RequestParam int topN,
            @RequestParam TransactionType type) {
        return ResponseEntity.ok(analyticsService.getTopCategories(userId, period, topN, type));
    }

    @PostMapping("/categories/aggregates")
    public ResponseEntity<List<CategoryAggregate>> getCategoryAggregates(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getCategoryAggregates(userId, period));
    }

    // --- Reasons ---
    @PostMapping("/reasons/overview")
    public ResponseEntity<ReasonAnalytics> getReasonOverview(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period,
            @RequestParam int topN) {
        return ResponseEntity.ok(analyticsService.getReasonOverview(userId, period, topN));
    }

    @PostMapping("/reasons/top")
    public ResponseEntity<List<RankingItem<ReasonSummary>>> getTopReasons(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period,
            @RequestParam int topN,
            @RequestParam TransactionType type) {
        return ResponseEntity.ok(analyticsService.getTopReasons(userId, period, topN, type));
    }

    // --- Budgets ---
    @PostMapping("/budgets/overview")
    public ResponseEntity<BudgetAnalytics> getBudgetOverview(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getBudgetOverview(userId, period));
    }

    @PostMapping("/budgets/executions")
    public ResponseEntity<List<BudgetAnalytics>> getBudgetExecutions(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getBudgetExecutions(userId, period));
    }

    @PostMapping("/budgets/exceeded")
    public ResponseEntity<List<BudgetAnalytics>> getExceededBudgets(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getExceededBudgets(userId, period));
    }

    @PostMapping("/budgets/near-limit")
    public ResponseEntity<List<BudgetAnalytics>> getNearLimitBudgets(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getNearLimitBudgets(userId, period));
    }

    // --- Scheduled / Projections ---
    @PostMapping("/scheduled/overview")
    public ResponseEntity<TransactionScheduleAnalytics> getScheduledOverview(
            @RequestParam Long userId,
            @RequestParam OffsetDateTime projectionStart,
            @RequestParam OffsetDateTime projectionEnd) {
        return ResponseEntity.ok(analyticsService.getScheduledOverview(userId, projectionStart, projectionEnd));
    }

    @PostMapping("/scheduled/projection")
    public ResponseEntity<TransactionScheduleAnalytics> makeProjection(
            @RequestParam Long userId,
            @RequestParam OffsetDateTime projectionStart,
            @RequestParam OffsetDateTime projectionEnd,
            @RequestParam TransactionType type) {
        return ResponseEntity.ok(analyticsService.makeProjection(userId, projectionStart, projectionEnd, type));
    }

    @GetMapping("/scheduled/active-count")
    public ResponseEntity<Long> getActiveScheduledTransactionCount(@RequestParam Long userId) {
        return ResponseEntity.ok(analyticsService.getActiveScheduledTransactionCount(userId));
    }

    @GetMapping("/scheduled/active-count-by-recurrence")
    public ResponseEntity<Map<RecurrenceType, Long>> getActiveScheduledCountByRecurrenceType(@RequestParam Long userId) {
        return ResponseEntity.ok(analyticsService.getActiveScheduledCountByRecurrenceType(userId));
    }

    @PostMapping("/scheduled/projections")
    public ResponseEntity<List<ScheduledProjection>> getScheduledProjections(
            @RequestParam Long userId,
            @RequestParam OffsetDateTime projectionStart,
            @RequestParam OffsetDateTime projectionEnd) {
        return ResponseEntity.ok(analyticsService.getScheduledProjections(userId, projectionStart, projectionEnd));
    }

    @PostMapping("/user/insights")
    public ResponseEntity<List<Insight>> getUserInsights(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getUserInsights(userId, period));
    }

    @PostMapping("/transactions/insights")
    public ResponseEntity<List<Insight>> getTransactionInsights(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getTransactionInsights(userId, period));
    }

    @PostMapping("/budgets/insights")
    public ResponseEntity<List<Insight>> getBudgetInsights(
            @RequestParam Long userId,
            @RequestBody AnalyticsPeriod period) {
        return ResponseEntity.ok(analyticsService.getBudgetInsights(userId, period));
    }

    @PostMapping("/scheduled/insights")
    public ResponseEntity<List<Insight>> getScheduledInsights(
            @RequestParam Long userId,
            @RequestParam OffsetDateTime projectionStart,
            @RequestParam OffsetDateTime projectionEnd) {
        return ResponseEntity.ok(analyticsService.getScheduledInsights(userId, projectionStart, projectionEnd));
    }

}

