package com.elias.mismonedas.insights.controller;

import com.elias.mismonedas.insights.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.insights.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.mismonedas.insights.service.analytics.BudgetAnalyticsService;
import com.elias.mismonedas.insights.service.analytics.TransactionAnalyticsService;
import com.elias.mismonedas.planning.dto.budget.BudgetExecutionResponse;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            description = "Genera el dashboard de balance del periodo. Sin walletId consolida todas las billeteras del usuario en su moneda base.",
            operationId = "getBalanceDashboard",
            tags = {"Analytics"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dashboard generado",
                            content = @Content(schema = @Schema(implementation = TransactionsDashboard.class))),
                    @ApiResponse(responseCode = "400", description = "Request invalido"),
                    @ApiResponse(responseCode = "404", description = "Billetera no encontrada")
            }
    )
    @GetMapping("/dashboard/balance")
    public ResponseEntity<DashboardResponse> balance(
            @Parameter(description = "Fecha de inicio del periodo (yyyy-MM-dd)", example = "2026-06-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha de fin del periodo (yyyy-MM-dd)", example = "2026-06-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "ID de billetera para filtrar (opcional). Sin valor consolida todas las billeteras.", example = "1")
            @RequestParam(required = false) Long walletId) {
        return ResponseEntity.ok(tas.buildBalanceDashboard(new DateRange(from, to), walletId));
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
    @GetMapping("/dashboard/budgets")
    public ResponseEntity<DashboardResponse> budgets(
            @Parameter(description = "Fecha de inicio del periodo (yyyy-MM-dd)", example = "2026-06-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha de fin del periodo (yyyy-MM-dd)", example = "2026-06-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(bas.buildDashboard(new DateRange(from, to)));
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
    @GetMapping("/budgets/{id}/execution")
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
