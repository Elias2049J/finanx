package com.elias.finanx.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para registrar una transaccion asociada a un usuario, categoria y opcionalmente a un motivo o meta de ahorro")
public class TransactionRequest {

    @NotNull
    @Schema(
            description = "Identificador unico de la billetera asociada a la transaccion",
            example = "1"
    )
    private Long walletId;

    @NotNull
    @Schema(
            description = "Identificador unico de la categoria asociada a la transaccion",
            example = "101"
    )
    private Long categoryId;

    @Schema(
            description = "Identificador del motivo asociado a la transaccion, si aplica",
            example = "501"
    )
    private Long reasonId;

    @Schema(
            description = "Descripcion de la transaccion",
            example = "Compra de libros universitarios"
    )
    private String description;

    @Schema(
            description = "Identificador de la meta de ahorro asociada, si aplica",
            example = "3001"
    )
    private Long savingGoalId;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Schema(
            description = "Monto de la transaccion (debe ser mayor a 0)",
            example = "150.75"
    )
    private BigDecimal amount;

    @NotNull
    @Schema(
            description = "Metodo de pago utilizado en la transaccion",
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
    private TransactionType type;
}

