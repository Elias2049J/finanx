package com.elias.finanx.dto.analytics.dashboard;

import com.elias.finanx.dto.analytics.component.Ranking;
import com.elias.finanx.dto.analytics.component.TimeLine;
import com.elias.finanx.dto.category.CategorySummary;
import com.elias.finanx.dto.reason.ReasonSummary;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dashboard de transacciones con rankings y lineas de tiempo de ingresos y gastos")
public class TransactionsDashboard extends DashboardResponse {

    @Schema(description = "Balance del periodo")
    private BigDecimal balance;

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
            description = "Ranking de las categorias principales"
    )
    private Ranking<CategorySummary> topCategories;

    @Schema(
            description = "Ranking de los principales motivos de gasto"
    )
    private Ranking<ReasonSummary> topSpentReasons;

    @Schema(
            description = "Ranking de los principales motivos de ingreso"
    )
    private Ranking<ReasonSummary> topIncomeReasons;
}

