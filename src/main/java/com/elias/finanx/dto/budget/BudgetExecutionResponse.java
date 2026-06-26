package com.elias.finanx.dto.budget;

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
@Schema(description = "Respuesta de ejecucion de presupuesto con montos gastados, restantes y porcentaje de ejecucion")
public class BudgetExecutionResponse extends BudgetResponse {

    @Schema(
            description = "Monto total gastado en el presupuesto",
            example = "2500.00"
    )
    private BigDecimal spentAmount;

    @Schema(
            description = "Monto restante disponible en el presupuesto",
            example = "500.00"
    )
    private BigDecimal remainingAmount;

    @Schema(
            description = "Porcentaje de ejecucion del presupuesto respecto al limite definido",
            example = "83.33"
    )
    private BigDecimal executionPercentage;
}

