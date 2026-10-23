package com.elias.mismonedas.wallets.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Balance neto consolidado de todas las billeteras del usuario en su moneda principal")
public class WalletBalanceResponse {

    @Schema(description = "Balance neto consolidado", example = "1234.56")
    private BigDecimal balance;

    @Schema(description = "Moneda principal en la que esta expresado el balance", example = "USD")
    private String currencyCode;

    @Schema(description = "Indica si el consolidado quedo incompleto por tasas de cambio faltantes")
    private boolean partial;

    @Schema(description = "Pares de moneda sin tasa disponible (origen->destino) excluidos del consolidado")
    private List<String> missingRates;
}
