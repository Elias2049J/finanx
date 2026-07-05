package com.elias.finanx.controller;

import com.elias.finanx.dto.analytics.dashboard.DashboardResponse;
import com.elias.finanx.dto.budget.BudgetExecutionResponse;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.service.analytics.BudgetAnalyticsService;
import com.elias.finanx.service.analytics.TransactionAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @Operation(
            summary = "Dashboard de balance",
            description = "Genera el dashboard de balance basado en un periodo y un prompt opcional.",
            operationId = "getBalanceDashboard",
            tags = {"Analytics"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dashboard generado"),
                    @ApiResponse(responseCode = "400", description = "Request invalido")
            }
    )
    @PostMapping("/dashboard/balance")
    public ResponseEntity<DashboardResponse> balance(
            @Parameter(description = "Periodo") @Valid @RequestBody PeriodRequest request)
            throws HttpException, IOException {
        return ResponseEntity.ok(tas.buildDashboard(request));
    }

    @Operation(
            summary = "Dashboard de presupuestos",
            description = "Genera el dashboard de presupuestos.",
            operationId = "getBudgetsDashboard",
            tags = {"Analytics"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dashboard generado"),
                    @ApiResponse(responseCode = "400", description = "Request invalido")
            }
    )
    @PostMapping("/dashboard/budgets")
    public ResponseEntity<DashboardResponse> budgets(
            @Valid @RequestBody PeriodRequest request)
            throws HttpException, IOException {
        return ResponseEntity.ok(bas.buildDashboard(request));
    }

    @Operation(
            summary = "Ejecucion de presupuesto",
            description = "Obtiene la ejecucion de un presupuesto por ID.",
            operationId = "getBudgetExecution",
            tags = {"Analytics"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Ejecucion encontrada"),
                    @ApiResponse(responseCode = "404", description = "No encontrado")
            }
    )
    @PostMapping("/budgets/{id}/execution")
    public ResponseEntity<BudgetExecutionResponse> getExecution(
            @Parameter(description = "ID presupuesto") @PathVariable Long id) {
        return ResponseEntity.ok(bas.getBudgetExecution(id));
    }

    @Operation(
            summary = "Ejecuciones por periodo",
            description = "Obtiene ejecuciones de presupuestos por periodo.",
            operationId = "getBudgetsExecutionsByPeriod",
            tags = {"Analytics"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "400", description = "Request invalido")
            }
    )
    @PostMapping("/budgets/executions")
    public ResponseEntity<List<BudgetExecutionResponse>> getExecutionsByPeriod(
            @Valid @RequestBody PeriodRequest request) {
        return ResponseEntity.ok(bas.getBudgetsExecutions(request));
    }

    /*

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
}