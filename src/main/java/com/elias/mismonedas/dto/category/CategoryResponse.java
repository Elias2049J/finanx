package com.elias.mismonedas.dto.category;

import com.elias.mismonedas.entity.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta que representa una categoria de transaccion registrada en el sistema")
public class CategoryResponse {

    @Schema(
            description = "Identificador unico de la categoria",
            example = "101"
    )
    private Long id;

    @Schema(
            description = "Nombre de la categoria",
            example = "Alimentacion"
    )
    private String name;

    @Schema(
            description = "Descripcion detallada de la categoria",
            example = "Compras de alimentos, restaurantes y productos de consumo diario"
    )
    private String description;

    @Schema(
            description = "Tipo de transaccion asociado a la categoria",
            implementation = TransactionType.class,
            example = "SPENT"
    )
    private TransactionType type;

    @Schema(
            description = "Indica si la categoria esta activa",
            example = "true"
    )
    private Boolean active;

    @Schema(
            description = "Fecha y hora de creacion de la categoria",
            example = "2026-06-25T23:59:59"
    )
    private LocalDateTime createdAt;

    @Schema(
            description = "Fecha y hora en que la categoria fue deshabilitada, si aplica",
            example = "2026-07-01T12:00:00"
    )
    private LocalDateTime disabledAt;
}
