package com.elias.finanx.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SendMessageRequest", description = "Mensaje del usuario al chatbot")
public class SendMessageRequest {

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Texto del mensaje", example = "¿Como están mis gastos este mes?")
    private String message;
}