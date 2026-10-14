package com.elias.mismonedas.insights.dto.insight;

import com.elias.mismonedas.insights.dto.analytics.component.InsightSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta con el insight financiero generado")
public class InsightResponse {

    @Schema(description = "Resumen de insights generados")
    private InsightSummary summary;
}
