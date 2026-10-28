package com.elias.mismonedas.scheduling.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Clave de la ocurrencia pendiente que se desea confirmar u omitir")
public class OccurrenceKeyRequest {

    @NotBlank
    @Schema(
            description = "Clave de la ocurrencia pendiente devuelta al listar el recordatorio",
            example = "schedule:12:1761700000000",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String occurrenceKey;
}
