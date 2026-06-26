package com.elias.finanx.dto.reason;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "Respuesta que representa un motivo asociado a una transaccion")
public class ReasonResponse {

    @Schema(
            description = "Identificador unico del motivo",
            example = "501"
    )
    private Long id;

    @Schema(
            description = "Descripcion del motivo",
            example = "Pago de servicios basicos"
    )
    private String description;

    @Schema(
            description = "Identificador unico del usuario asociado al motivo",
            example = "42"
    )
    private Long userId;

    @Schema(
            description = "Fecha y hora de creacion del motivo",
            example = "2026-06-25T23:59:59"
    )
    private LocalDateTime createdAt;

    @Schema(
            description = "Fecha y hora en que el motivo fue deshabilitado, si aplica",
            example = "2026-07-01T12:00:00"
    )
    private LocalDateTime disabledAt;

    @Schema(
            description = "Indica si el motivo esta activo",
            example = "true"
    )
    private boolean active;
}
