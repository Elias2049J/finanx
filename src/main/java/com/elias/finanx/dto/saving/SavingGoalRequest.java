package com.elias.finanx.dto.saving;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Data
@Schema(description = "Solicitud para registrar una nueva meta de ahorro en el sistema")
public class SavingGoalRequest {

    @NotBlank
    @Schema(
            description = "Descripcion de la meta de ahorro",
            example = "Ahorro para comprar una laptop"
    )
    private String description;

    @NotNull
    @Schema(
            description = "Monto objetivo de la meta de ahorro",
            example = "2500.00"
    )
    private BigDecimal targetAmount;

    @NotNull
    @Schema(
            description = "Fecha limite para alcanzar la meta (formato yyyy-MM-dd)",
            example = "2026-12-31"
    )
    private LocalDate deadline;

    @NotNull
    @Schema(
            description = "Identificador unico del usuario asociado a la meta",
            example = "42"
    )
    private Long userId;
}
