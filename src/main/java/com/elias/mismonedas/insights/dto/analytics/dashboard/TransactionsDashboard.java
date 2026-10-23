package com.elias.mismonedas.insights.dto.analytics.dashboard;

import com.elias.mismonedas.catalog.dto.category.CategorySummary;
import com.elias.mismonedas.catalog.dto.reason.ReasonSummary;
import com.elias.mismonedas.insights.dto.analytics.component.Ranking;
import com.elias.mismonedas.insights.dto.analytics.component.TimeLine;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
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
@Schema(description = "Dashboard de transacciones con rankings y lineas de tiempo de ingresos y gastos")
public class TransactionsDashboard extends DashboardResponse {

    @Schema(description = "Balance del periodo")
    private BigDecimal balance;

    @Schema(description = "Moneda base del consolidado (preferencia del usuario)", example = "PEN")
    private String currencyCode;

    @Schema(description = "Indica si el consolidado quedo incompleto por tasas de cambio faltantes")
    private boolean partial;

    @Schema(description = "Pares de moneda sin tasa disponible (origen->destino) excluidos del consolidado")
    private List<String> missingRates;

    @Schema(
            description = "Ranking de categorias con metricas resumidas"
    )
    private Ranking<CategorySummary> summaryByCategory;

    @Schema(
            description = "Ranking de metodos de pago con metricas resumidas"
    )
    private Ranking<PaymentMethod> summaryByPaymentMethod;

    @Schema(
            description = "Ranking por tipo de transaccion con metricas resumidas"
    )
    private Ranking<TransactionType> summaryByType;

    @Schema(
            description = "Linea de tiempo de gastos diarios"
    )
    private TimeLine<TransactionResponse> dailyExpenses;

    @Schema(
            description = "Linea de tiempo de ingresos diarios"
    )
    private TimeLine<TransactionResponse> dailyIncome;

    @Schema(
            description = "Ranking de categorias de gasto, con participacion sobre el total de gastos"
    )
    private Ranking<CategorySummary> topSpentCategories;

    @Schema(
            description = "Ranking de categorias de ingreso, con participacion sobre el total de ingresos"
    )
    private Ranking<CategorySummary> topIncomeCategories;

    @Schema(
            description = "Ranking de los principales motivos de gasto"
    )
    private Ranking<ReasonSummary> topSpentReasons;

    @Schema(
            description = "Ranking de los principales motivos de ingreso"
    )
    private Ranking<ReasonSummary> topIncomeReasons;
}

