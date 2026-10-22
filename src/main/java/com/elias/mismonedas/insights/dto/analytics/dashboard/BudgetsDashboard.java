package com.elias.mismonedas.insights.dto.analytics.dashboard;

import com.elias.mismonedas.catalog.dto.category.CategoryResponse;
import com.elias.mismonedas.insights.dto.analytics.component.AggregateBy;
import com.elias.mismonedas.insights.dto.analytics.component.Ranking;
import com.elias.mismonedas.insights.dto.analytics.component.TimeLine;
import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import com.elias.mismonedas.planning.dto.budget.BudgetExecutionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dashboard de presupuestos con agregados por salud y categoria, evolucion de ejecucion y metricas globales")
public class BudgetsDashboard extends DashboardResponse {

    @Schema(description = "Moneda base del consolidado (preferencia del usuario)", example = "PEN")
    private String currencyCode;

    @Schema(
            description = "Lista de agregados por estado de salud del presupuesto"
    )
    private List<AggregateBy<BudgetHealth>> byHealth;

    @Schema(
            description = "Lista de agregados por categoria de ejecucion presupuestal"
    )
    private List<AggregateBy<BudgetExecutionResponse>> byCategory;

    @Schema(
            description = "Linea de tiempo de la evolucion de ejecucion de presupuestos"
    )
    private TimeLine<BudgetExecutionResponse> executionEvolution;

    @Schema(
            description = "Ranking de categorias mas ejecutadas"
    )
    private Ranking<CategoryResponse> mostExecuted;

    @Schema(
            description = "Ranking de categorias con mayor saldo restante"
    )
    private Ranking<CategoryResponse> mostRemaining;

    @Schema(
            description = "Monto limite total de todos los presupuestos",
            example = "50000.00"
    )
    private BigDecimal totalBudgetLimitAmount;

    @Schema(
            description = "Monto total gastado en todos los presupuestos",
            example = "37500.00"
    )
    private BigDecimal totalBudgetSpentAmount;

    @Schema(
            description = "Monto total restante en todos los presupuestos",
            example = "12500.00"
    )
    private BigDecimal totalBudgetRemainingAmount;
}
