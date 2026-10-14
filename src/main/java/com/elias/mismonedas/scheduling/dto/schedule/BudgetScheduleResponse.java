package com.elias.mismonedas.scheduling.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Respuesta que representa una programacion de presupuesto con limites y alertas")
public class BudgetScheduleResponse extends ScheduleResponse {

    @Schema(
            description = "Indica si el presupuesto debe generar alertas",
            example = "true"
    )
    private Boolean alertable;

    @Schema(
            description = "Porcentaje de alerta respecto al limite del presupuesto",
            example = "80"
    )
    private Integer percentAlert;

    @Schema(
            description = "Monto limite del presupuesto",
            example = "1500.00"
    )
    private BigDecimal limitAmount;

    @Schema(
            description = "Descripcion del presupuesto",
            example = "Presupuesto mensual de alimentacion"
    )
    private String description;
}

