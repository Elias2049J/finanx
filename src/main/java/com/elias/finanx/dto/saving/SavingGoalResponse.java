package com.elias.finanx.dto.saving;

import com.elias.finanx.entity.enums.SavingGoalState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta que representa una meta de ahorro con su estado y metricas de progreso")
public class SavingGoalResponse {

    @Schema(description = "Identificador unico de la meta de ahorro", example = "1001")
    private Long id;

    @Schema(description = "Descripcion de la meta de ahorro", example = "Ahorro para comprar una laptop")
    private String description;

    @Schema(description = "Monto objetivo de la meta de ahorro", example = "2500.00")
    private BigDecimal targetAmount;

    @Schema(description = "Codigo de moneda de la meta", example = "PEN")
    private String currencyCode;

    @Schema(description = "Monto acumulado hasta el momento", example = "1200.00")
    private BigDecimal accumulated;

    @Schema(description = "Fecha limite para alcanzar la meta", example = "2026-12-31T23:59:59")
    private LocalDateTime deadline;

    @Schema(description = "Fecha y hora de creacion de la meta", example = "2026-06-25T23:59:59")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha y hora en que la meta fue completada, si aplica", example = "2026-11-15T10:00:00")
    private LocalDateTime completedAt;

    @Schema(description = "Fecha y hora en que la meta fue deshabilitada, si aplica", example = "2026-11-20T12:00:00")
    private LocalDateTime disabledAt;

    @Schema(description = "Estado actual de la meta de ahorro", implementation = SavingGoalState.class, example = "RUNNING")
    private SavingGoalState state;

    @Schema(description = "Indica si la meta esta activa", example = "true")
    private Boolean active;

    @Schema(description = "Identificador unico del usuario asociado a la meta", example = "42")
    private Long userId;

    @Schema(description = "Porcentaje de progreso hacia la meta", example = "48.0")
    private double progressPercentage;

    @Schema(description = "Cantidad de transacciones asociadas a la meta", example = "15")
    private int transactionsCount;

    @Schema(description = "Monto pendiente para alcanzar la meta", example = "1300.00")
    private BigDecimal outstanding;

    @Schema(description = "Numero de dias restantes hasta la fecha limite", example = "180")
    private long daysRemaining;

    @Schema(description = "Monto promedio aportado por transaccion", example = "80.00")
    private BigDecimal averageContribution;

    @Schema(description = "Indica si la meta ya fue completada", example = "false")
    private boolean completed;

    @Schema(description = "Fecha estimada de cumplimiento de la meta, calculada en base al ritmo de aportes", example = "2026-11-30T23:59:59")
    private LocalDateTime estimatedCompletionDate;
}
