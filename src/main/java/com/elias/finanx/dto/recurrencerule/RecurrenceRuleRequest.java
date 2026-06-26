package com.elias.finanx.dto.recurrencerule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.time.LocalDate;

import com.elias.finanx.entity.enums.RecurrenceType;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para definir una regla de recurrencia en transacciones o presupuestos")
public class RecurrenceRuleRequest {

    @NotNull
    @Schema(
            description = "Tipo de recurrencia (diaria, semanal, mensual, anual)",
            implementation = RecurrenceType.class,
            example = "WEEKLY"
    )
    private RecurrenceType recurrenceType;

    @NotNull
    @Schema(
            description = "Intervalo de repeticion segun el tipo de recurrencia",
            example = "2"
    )
    private int interval;

    @Schema(
            description = "Dia especifico de la semana en que aplica la recurrencia (solo relevante si recurrenceType = WEEKLY)",
            implementation = DayOfWeek.class,
            example = "MONDAY"
    )
    private DayOfWeek dayOfWeek;

    @NotNull
    @Schema(
            description = "Fecha de inicio de la recurrencia (formato yyyy-MM-dd)",
            example = "2026-07-01"
    )
    private LocalDate start;

    @NotNull
    @Schema(
            description = "Fecha de fin de la recurrencia (formato yyyy-MM-dd)",
            example = "2026-12-31"
    )
    private LocalDate end;
}
