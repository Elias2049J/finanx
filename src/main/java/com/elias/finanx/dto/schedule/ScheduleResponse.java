package com.elias.finanx.dto.schedule;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.finanx.entity.BudgetSchedule;
import com.elias.finanx.entity.enums.ScheduleState;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionScheduleResponse.class, name = "TRANSACTION"),
        @JsonSubTypes.Type(value = BudgetScheduleResponse.class, name = "BUDGET")
})
@Schema(description = "Respuesta que representa una programacion asociada a un usuario y categoria, incluyendo estado y regla de recurrencia")
public class ScheduleResponse {

    @Schema(description = "Identificador unico de la programacion", example = "2001")
    private Long id;

    @Schema(description = "Identificador unico del usuario asociado a la programacion", example = "42")
    private Long userId;

    @Schema(description = "Identificador unico de la categoria asociada a la programacion", example = "101")
    private Long categoryId;

    @Schema(description = "Nombre de la categoria asociada a la programacion", example = "Alimentacion")
    private String categoryName;

    @Schema(description = "Fecha y hora de la proxima ejecucion programada", example = "2026-07-01T08:00:00")
    private LocalDateTime nextRunAt;

    @Schema(description = "Fecha y hora de creacion de la programacion", example = "2026-06-25T23:59:59")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha y hora de la ultima ejecucion realizada", example = "2026-06-20T08:00:00")
    private LocalDateTime lastRunAt;

    @Schema(description = "Fecha y hora en que la programacion fue deshabilitada, si aplica", example = "2026-07-15T12:00:00")
    private LocalDateTime disabledAt;

    @Schema(description = "Indica si la programacion esta activa", example = "true")
    private Boolean active;

    @Schema(description = "Estado actual de la programacion", implementation = ScheduleState.class, example = "RUNNING")
    private ScheduleState state;

    @Schema(description = "Regla de recurrencia asociada a la programacion", implementation = RecurrenceRuleResponse.class)
    private RecurrenceRuleResponse recurrenceRule;
}

