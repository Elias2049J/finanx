package com.elias.mismonedas.dto.analytics.component;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Objeto que representa un insight con severidad, titulo, descripcion y accion recomendada")
public class Insight {

    @Schema(description = "Nivel de severidad", example = "WARNING")
    private InsightSeverity severity;

    @Schema(description = "Titulo breve", example = "Presupuesto cercano al limite")
    private String title;

    @Schema(description = "Descripcion detallada", example = "El gasto ha alcanzado el 85% del presupuesto asignado")
    private String description;

    @Schema(description = "Accion recomendada", example = "Revisar gastos futuros y ajustar si es necesario")
    private String action;

    @Schema(description = "Enumeracion de severidades posibles")
    public enum InsightSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
}
