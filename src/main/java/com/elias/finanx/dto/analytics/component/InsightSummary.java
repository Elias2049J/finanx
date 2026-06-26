package com.elias.finanx.dto.analytics.component;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen de insights generados")
public class InsightSummary {

    @Schema(
            description = "Lista de insights"
    )
    private List<Insight> insights;

    @Schema(
            description = "Numero total de insights",
            example = "5"
    )
    private int insightsCount;
}
