package com.elias.mismonedas.catalog.dto.reason;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen de un motivo asociado a una transaccion")
public class ReasonSummary {

    @Schema(
            description = "Identificador unico del motivo",
            example = "501"
    )
    private Long id;

    @Schema(
            description = "Descripcion breve del motivo",
            example = "Compra de alimentos"
    )
    private String description;
}

