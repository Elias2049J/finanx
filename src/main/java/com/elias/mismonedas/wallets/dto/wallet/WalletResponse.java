package com.elias.mismonedas.wallets.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta que representa una billetera")
public class WalletResponse {

    @Schema(description = "Identificador unico de la billetera", example = "1")
    private Long id;

    @Schema(description = "Identificador del usuario propietario", example = "42")
    private Long userId;

    @Schema(description = "Codigo de moneda (ISO 4217)", example = "PEN")
    private String currencyCode;

    @Schema(description = "Balance actual", example = "1500.00")
    private BigDecimal balance;

    @Schema(description = "Indica si es la billetera principal", example = "true")
    private boolean primary;
}
