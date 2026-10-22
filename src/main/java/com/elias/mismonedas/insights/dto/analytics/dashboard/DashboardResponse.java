package com.elias.mismonedas.insights.dto.analytics.dashboard;

import com.elias.mismonedas.insights.dto.analytics.component.Aggregate;
import com.elias.mismonedas.insights.dto.analytics.component.InsightSummary;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionsDashboard.class, name = "BALANCE"),
        @JsonSubTypes.Type(value = SavingGoalsDashboard.class, name = "SAVINGS"),
        @JsonSubTypes.Type(value = TSchedulesDashboard.class, name = "TSCHEDULES"),
        @JsonSubTypes.Type(value = BudgetsDashboard.class, name = "BUDGETS")
})
@Schema(
        name = "DashboardResponse",
        description = "Respuesta base de un dashboard con periodo, titulo, fecha de creacion, resumen de insights y metricas agregadas")
public abstract class DashboardResponse {

    @Schema(
            description = "Periodo de referencia del dashboard en texto legible",
            example = "01 de junio de 2026 a 30 de junio de 2026"
    )
    private String dateRangeLabel;

    @Schema(
            description = "Titulo descriptivo del dashboard",
            example = "Resumen financiero mensual"
    )
    private String title;

    @Schema(
            description = "Fecha y hora de creacion del dashboard",
            example = "2026-06-25T23:57:00"
    )
    private LocalDateTime createdAt;

    @Schema(
            description = "Resumen de insights generados para el periodo",
            implementation = InsightSummary.class
    )
    private InsightSummary insightSummary;

    @Schema(
            description = "Agregado general con metricas calculadas",
            implementation = Aggregate.class
    )
    private Aggregate aggregate;
}

