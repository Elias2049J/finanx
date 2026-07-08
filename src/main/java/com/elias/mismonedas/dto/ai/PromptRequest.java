package com.elias.mismonedas.dto.ai;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de prompt para el LLM")
public class PromptRequest {

    @NotBlank
    @Schema(
            description = "Texto de la consulta del usuario al modelo",
            example = "Analiza mis gastos mensuales y dame insights"
    )
    private String userPrompt;

    @Schema(
            description = "Contexto adicional en formato string (puede ser cualquier dato serializado o texto que el LLM debe considerar)",
            example = "{ \"ingresos\": 2500, \"gastos\": 1800, \"mes\": \"Junio\" }"
    )
    private String context;
}
