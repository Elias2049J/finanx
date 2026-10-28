package com.elias.mismonedas.scheduling.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resultado de confirmar una ocurrencia pendiente")
public class OccurrenceConfirmationResponse {

    @Schema(description = "Clave de la ocurrencia confirmada", example = "schedule:12:1761700000000")
    private String occurrenceKey;

    @Schema(description = "Identificador del movimiento creado, si el recordatorio era de movimientos", example = "42")
    private Long transactionId;

    @Schema(description = "Identificador del presupuesto creado, si el recordatorio era de presupuestos", example = "7")
    private Long budgetId;
}
