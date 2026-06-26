package com.elias.finanx.dto.budget;

import com.elias.finanx.entity.enums.BudgetHealth;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.elias.finanx.entity.enums.BudgetState;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta de presupuesto con informacion detallada de limites, estado y periodo")
public class BudgetResponse {

    @Schema(
            description = "Identificador unico del presupuesto",
            example = "101"
    )
    private Long id;

    @Schema(
            description = "Identificador del usuario propietario del presupuesto",
            example = "123"
    )
    private Long userId;

    @Schema(
            description = "Identificador de la categoria asociada al presupuesto",
            example = "45"
    )
    private Long categoryId;

    @Schema(
            description = "Nombre de la categoria asociada al presupuesto",
            example = "Alimentos"
    )
    private String categoryName;

    @Schema(
            description = "Identificador de la programacion asociada al presupuesto",
            example = "12"
    )
    private Long scheduleId;

    @Schema(
            description = "Indica si el presupuesto debe generar alertas",
            example = "true"
    )
    private Boolean alertable;

    @Schema(
            description = "Porcentaje de alerta respecto al limite definido",
            example = "80"
    )
    private Integer percentAlert;

    @Schema(
            description = "Monto limite del presupuesto",
            example = "5000.00"
    )
    private BigDecimal limitAmount;

    @Schema(
            description = "Descripcion del presupuesto",
            example = "Presupuesto mensual para alimentos"
    )
    private String description;

    @Schema(
            description = "Estado actual del presupuesto",
            implementation = BudgetState.class,
            example = "ACTIVE"
    )
    private BudgetState state;

    @Schema(
            description = "Salud del presupuesto segun su ejecucion",
            implementation = BudgetHealth.class,
            example = "OK"
    )
    private BudgetHealth health;

    @Schema(
            description = "Fecha y hora de inicio del presupuesto",
            example = "2026-07-01T00:00:00"
    )
    private LocalDateTime start;

    @Schema(
            description = "Fecha y hora de fin del presupuesto",
            example = "2026-07-31T23:59:59"
    )
    private LocalDateTime end;

    @Schema(
            description = "Fecha y hora de creacion del presupuesto",
            example = "2026-06-25T12:00:00"
    )
    private LocalDateTime createdAt;

    @Schema(
            description = "Fecha y hora de desactivacion del presupuesto",
            example = "2026-06-30T18:00:00"
    )
    private LocalDateTime disabledAt;
}
