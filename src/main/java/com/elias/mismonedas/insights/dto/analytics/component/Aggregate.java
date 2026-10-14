package com.elias.mismonedas.insights.dto.analytics.component;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Resumen/Agregado")
public class Aggregate {

    @Schema(description = "Titulo o nombre del agregado", example = "Transacciones de Junio")
    private String title;

    @Schema(description = "Monto total acumulado", example = "12500.75")
    private BigDecimal totalAmount;

    @Schema(description = "Monto promedio calculado", example = "3125.18")
    private BigDecimal averageAmount;

    @Schema(description = "Numero de ocurrencias consideradas", example = "4")
    private long occurrenceCount;

    @Schema(description = "Valor maximo encontrado", example = "5000.00")
    private BigDecimal max;

    @Schema(description = "Valor minimo encontrado", example = "100.00")
    private BigDecimal min;
}
