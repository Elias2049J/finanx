package com.elias.mismonedas.scheduling.domain.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado de una ocurrencia proyectada en el calendario")
public enum OccurrenceStatus {

    @Schema(description = "Ocurrencia vencida que espera confirmacion manual del usuario")
    PENDING,

    @Schema(description = "Ocurrencia futura proyectada a partir de la regla de recurrencia")
    UPCOMING
}
