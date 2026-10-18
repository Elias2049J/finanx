package com.elias.mismonedas.insights.controller;

import com.elias.mismonedas.insights.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.insights.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.mismonedas.insights.service.analytics.BudgetAnalyticsService;
import com.elias.mismonedas.insights.service.analytics.TransactionAnalyticsService;
import com.elias.mismonedas.planning.dto.budget.BudgetExecutionResponse;
import com.elias.mismonedas.shared.dto.date.PeriodRequest;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.apache.http.HttpException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
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
                    @ApiResponse(responseCode = "200", description = "Dashboard generado",
                            content = @Content(schema = @Schema(implementation = TransactionsDashboard.class))),
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
                    @ApiResponse(responseCode = "200", description = "Dashboard generado",
                            content = @Content(schema = @Schema(implementation = BudgetsDashboard.class))),
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
            description = "Obtiene ejecuciones de presupuestos paginadas por un rango de fechas del usuario.",
            operationId = "getBudgetsExecutionsByPeriod",
            tags = {"Analytics"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "400", description = "Request invalido")
            }
    )
    @GetMapping("/budgets/executions")
    public ResponseEntity<PageResponse<BudgetExecutionResponse>> getExecutionsByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(bas.getBudgetsExecutions(from, to, pageable));
    }













}
