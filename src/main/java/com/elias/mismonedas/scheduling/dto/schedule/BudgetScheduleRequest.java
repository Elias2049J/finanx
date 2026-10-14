package com.elias.mismonedas.scheduling.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Solicitud para crear o actualizar una programacion de presupuesto con limites y alertas")
public class BudgetScheduleRequest extends ScheduleRequest {

    @NotNull
    @Schema(
            description = "Indica si el presupuesto debe generar alertas",
            example = "true"
    )
    private Boolean alertable;

    @NotNull
    @Schema(
            description = "Porcentaje de alerta respecto al limite del presupuesto",
            example = "80"
    )
    private Integer percentAlert;

    @NotNull
    @Schema(
            description = "Monto limite del presupuesto",
            example = "1500.00"
    )
    private BigDecimal limitAmount;

    @NotBlank
    @Schema(
            description = "Descripcion del presupuesto",
            example = "Presupuesto mensual de alimentacion"
    )
    private String description;
}

