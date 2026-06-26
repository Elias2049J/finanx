package com.elias.finanx.dto.recurrencerule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.elias.finanx.entity.enums.RecurrenceType;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Respuesta que representa una regla de recurrencia aplicada a transacciones o presupuestos")
public class RecurrenceRuleResponse {

    @Schema(
            description = "Identificador unico de la regla de recurrencia",
            example = "1001"
    )
    private Long id;

    @Schema(
            description = "Tipo de recurrencia (diaria, semanal, mensual, anual)",
            implementation = RecurrenceType.class,
            example = "WEEKLY"
    )
    private RecurrenceType recurrenceType;

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

    @Schema(
            description = "Fecha y hora de inicio de la recurrencia",
            example = "2026-07-01T08:00:00"
    )
    private LocalDateTime start;

    @Schema(
            description = "Fecha y hora de fin de la recurrencia",
            example = "2026-12-31T23:59:59"
    )
    private LocalDateTime end;

    @Schema(
            description = "Duracion total del periodo de recurrencia en dias",
            example = "183"
    )
    private long durationDays;
}

