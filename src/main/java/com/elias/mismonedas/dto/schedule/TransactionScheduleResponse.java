package com.elias.mismonedas.dto.schedule;

import com.elias.mismonedas.entity.enums.PaymentMethod;
import com.elias.mismonedas.entity.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Respuesta que representa una programacion de transacciones asociada a un usuario y categoria")
public class TransactionScheduleResponse extends ScheduleResponse {

    @Schema(
            description = "Identificador de la billetera",
            example = "1"
    )
    private Long walletId;

    @Schema(
            description = "Moneda de la billetera",
            example = "PEN"
    )
    private String walletCurrency;

    @Schema(
            description = "Identificador del motivo asociado a la transaccion",
            example = "501"
    )
    private Long reasonId;

    @Schema(
            description = "Descripcion del motivo asociado a la transaccion",
            example = "Pago mensual de suscripcion a Netflix"
    )
    private String reasonDesc;

    @Schema(
            description = "Monto de la transaccion programada",
            example = "29.99"
    )
    private BigDecimal amount;

    @Schema(
            description = "Metodo de pago de la transaccion",
            implementation = PaymentMethod.class,
            example = "CREDIT_CARD"
    )
    private PaymentMethod paymentMethod;

    @Schema(
            description = "Tipo de transaccion (ingreso o gasto)",
            implementation = TransactionType.class,
            example = "SPENT"
    )
    private TransactionType transactionType;
}

