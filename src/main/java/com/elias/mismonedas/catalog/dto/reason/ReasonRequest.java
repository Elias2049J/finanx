package com.elias.mismonedas.catalog.dto.reason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "Solicitud para registrar un motivo asociado a una transaccion")
public class ReasonRequest {

    @NotBlank
    @Schema(
            description = "Descripcion del motivo",
            example = "Compra de utiles escolares"
    )
    private String description;

    @NotNull
    @Schema(
            description = "Identificador unico del usuario asociado al motivo",
            example = "42"
    )
    private Long userId;
}

