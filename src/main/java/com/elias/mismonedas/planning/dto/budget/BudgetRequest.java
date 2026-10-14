package com.elias.mismonedas.planning.dto.budget;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Solicitud para crear o actualizar un presupuesto con limites, alertas y periodo definido")
public class BudgetRequest {

    @NotNull
    @Schema(
            description = "Indica si el presupuesto debe generar alertas",
            example = "true"
    )
    private Boolean alertable;

    @Min(0)
    @Max(100)
    @Schema(
            description = "Porcentaje de alerta respecto al limite definido",
            example = "80"
    )
    private Integer percentAlert;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Schema(
            description = "Monto limite del presupuesto",
            example = "5000.00"
    )
    private BigDecimal limitAmount;

    @NotBlank
    @Schema(
            description = "Descripcion del presupuesto",
            example = "Presupuesto mensual para alimentos"
    )
    private String description;

    @NotNull
    @Schema(
            description = "Identificador del usuario propietario del presupuesto",
            example = "123"
    )
    private Long userId;

    @NotNull
    @Schema(
            description = "Identificador de la categoria asociada al presupuesto",
            example = "45"
    )
    private Long categoryId;

    @NotNull
    @Schema(
            description = "Fecha de inicio del presupuesto",
            example = "2026-07-01"
    )
    private LocalDate start;

    @NotNull
    @Schema(
            description = "Fecha de fin del presupuesto",
            example = "2026-07-31"
    )
    private LocalDate end;
}