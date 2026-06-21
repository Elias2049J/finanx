package com.elias.finanx.controller;

import com.elias.finanx.dto.analytics.dashboard.DashboardResponse;
import com.elias.finanx.dto.budget.BudgetExecutionResponse;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.service.analytics.BudgetAnalyticsService;
import com.elias.finanx.service.analytics.TransactionAnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final BudgetAnalyticsService bas;
    private final TransactionAnalyticsService tas;

    @PostMapping("/dashboard/balance")
    public ResponseEntity<DashboardResponse> balance(
            @Valid @RequestBody PeriodRequest request,
            @RequestParam(required = false) String prompt) throws HttpException, IOException {
        return ResponseEntity.ok(tas.buildDashboard(request, prompt));
    }/*

    @GetMapping("/dashboard/categories")
    public ResponseEntity<DashboardResponse> categories(
            @Valid @RequestBody PeriodRequest request) {
        return ResponseEntity.ok(analyticsService.generateBalanceDashboardResponse(request));
    }

    @GetMapping("/dashboard/savings")
    public ResponseEntity<DashboardResponse> savings(
            @Valid @RequestBody PeriodRequest request) {
        return ResponseEntity.ok(analyticsService.buildDashboard(request));
    }*/

    @PostMapping("/dashboard/budgets")
    public ResponseEntity<DashboardResponse> budgets(
            @Valid @RequestBody PeriodRequest request,
            @RequestParam(required = false) String prompt) throws HttpException, IOException {
        return ResponseEntity.ok(bas.buildDashboard(request, prompt));
    }

    @PostMapping("/budgets/{id}/execution")
    public ResponseEntity<BudgetExecutionResponse> getExecution(@PathVariable Long id) {
        return ResponseEntity.ok(bas.getBudgetExecution(id));
    }

    @PostMapping("/budgets/executions")
    public ResponseEntity<List<BudgetExecutionResponse>> getExecutionsByPeriod(
            @Valid @RequestBody PeriodRequest request) {
        return ResponseEntity.ok(bas.getBudgetsExecutions(request));
    }
}