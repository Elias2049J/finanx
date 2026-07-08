package com.elias.finanx.dto.insight;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para generar un insight financiero")
public class InsightRequest {

    @NotBlank
    @Schema(description = "Consulta del usuario para el analisis", example = "Analiza mis gastos del mes")
    private String query;
}
