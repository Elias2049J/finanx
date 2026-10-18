package com.elias.mismonedas.scheduling.dto.schedule;

import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Solicitud para crear o actualizar una programacion de transacciones asociada a un usuario y categoria")
public class TransactionScheduleRequest extends ScheduleRequest {

    @Schema(
            description = "Identificador de la billetera (null = usar la principal)",
            example = "1"
    )
    private Long walletId;

    @Schema(
            description = "Identificador del motivo asociado a la transaccion",
            example = "501"
    )
    private Long reasonId;

    @Schema(
            description = "Descripcion de la transaccion programada",
            example = "Pago mensual de suscripcion a Netflix"
    )
    private String description;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Schema(
            description = "Monto de la transaccion programada (debe ser mayor a 0)",
            example = "29.99"
    )
    private BigDecimal amount;

    @NotNull
    @Schema(
            description = "Metodo de pago de la transaccion",
            implementation = PaymentMethod.class,
            example = "CREDIT_CARD"
    )
    private PaymentMethod paymentMethod;

    @NotNull
    @Schema(
            description = "Tipo de transaccion (ingreso o gasto)",
            implementation = TransactionType.class,
            example = "SPENT"
    )
    private TransactionType transactionType;
}

