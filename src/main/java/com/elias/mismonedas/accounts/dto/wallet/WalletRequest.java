package com.elias.mismonedas.accounts.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para crear o actualizar una billetera")
public class WalletRequest {

    @NotBlank
    @Size(min = 3, max = 3)
    @Schema(description = "Codigo de moneda (ISO 4217)", example = "PEN")
    private String currencyCode;
    @Schema(description = "Indica si es la billetera principal", example = "true")
    private boolean primary;
}
