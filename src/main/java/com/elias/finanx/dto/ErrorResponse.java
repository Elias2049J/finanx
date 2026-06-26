package com.elias.finanx.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
@Getter
@Setter
@AllArgsConstructor
@ToString
@Schema(description = "Respuesta estandar de error devuelta por la API")
public class ErrorResponse {

    @Schema(
            description = "Metodo HTTP que origino el error",
            example = "POST"
    )
    private String method;

    @Schema(
            description = "Ruta del endpoint donde ocurrio el error",
            example = "/api/v1/transactions"
    )
    private String path;

    @Schema(
            description = "Mensaje descriptivo del error",
            example = "El monto no puede ser negativo"
    )
    private String message;

    @Schema(
            description = "Codigo de estado HTTP asociado al error",
            example = "400"
    )
    private int status;

    @Schema(
            description = "Marca de tiempo en que ocurrio el error (formato ISO 8601)",
            example = "2026-06-25T23:59:59"
    )
    private String timestamp;
}