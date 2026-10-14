package com.elias.mismonedas.scheduling.dto.schedule;

import com.elias.mismonedas.scheduling.dto.recurrencerule.RecurrenceRuleRequest;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionScheduleRequest.class, name = "TRANSACTION"),
        @JsonSubTypes.Type(value = BudgetScheduleRequest.class, name = "BUDGET")
})
@Data
@Schema(description = "Solicitud base para crear o actualizar una programacion asociada a un usuario y categoria")
public class ScheduleRequest {

    @NotNull
    @Schema(
            description = "Identificador unico del usuario asociado a la programacion",
            example = "42"
    )
    private Long userId;

    @NotNull
    @Schema(
            description = "Identificador unico de la categoria asociada a la programacion",
            example = "101"
    )
    private Long categoryId;

    @Valid
    @Schema(
            description = "Regla de recurrencia que define la periodicidad de la programacion",
            implementation = RecurrenceRuleRequest.class
    )
    private RecurrenceRuleRequest recurrenceRule;
}

