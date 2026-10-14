package com.elias.mismonedas.catalog.dto.category;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "Solicitud para crear o actualizar una categoria de transaccion")
public class CategoryRequest {

    @NotBlank
    @Schema(
            description = "Nombre de la categoria",
            example = "Alimentacion"
    )
    private String name;

    @NotBlank
    @Schema(
            description = "Descripcion de la categoria",
            example = "Compras de alimentos, restaurantes y productos de consumo diario"
    )
    private String description;

    @NotNull
    @Schema(
            description = "Identificador del usuario propietario de la categoria",
            example = "123"
    )
    private Long userId;

    @NotNull
    @Schema(
            description = "Tipo de transaccion asociado a la categoria",
            implementation = TransactionType.class,
            example = "SPENT"
    )
    private TransactionType type;
}
