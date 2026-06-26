package com.elias.finanx.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen de una categoria con identificador y nombre")
public class CategorySummary {

    @Schema(
            description = "Identificador unico de la categoria",
            example = "5"
    )
    private Long id;

    @Schema(
            description = "Nombre de la categoria",
            example = "Transporte"
    )
    private String name;
}

