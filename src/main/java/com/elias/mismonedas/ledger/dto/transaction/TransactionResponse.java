package com.elias.mismonedas.ledger.dto.transaction;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta que representa una transaccion registrada en el sistema")
public class TransactionResponse {

    @Schema(description = "Identificador unico de la transaccion", example = "10001")
    private Long id;

    @Schema(description = "Identificador unico de la billetera asociada a la transaccion", example = "1")
    private Long walletId;

    @Schema(description = "Moneda de la billetera", example = "PEN")
    private String walletCurrency;

    @Schema(description = "Indica si la billetera es principal", example = "true")
    private boolean walletPrimary;

    @Schema(description = "Identificador unico de la categoria asociada a la transaccion", example = "101")
    private Long categoryId;

    @Schema(description = "Nombre de la categoria asociada a la transaccion", example = "Alimentacion")
    private String categoryName;

    @Schema(description = "Identificador del motivo asociado a la transaccion, si aplica", example = "501")
    private Long reasonId;

    @Schema(description = "Descripcion del motivo asociado a la transaccion", example = "Compra de utiles escolares")
    private String reasonDescription;

    @Schema(description = "Identificador de la meta de ahorro asociada, si aplica", example = "3001")
    private Long savingGoalId;

    @Schema(description = "Identificador de la programacion asociada, si aplica", example = "2001")
    private Long scheduleId;

    @Schema(description = "Identificador del recibo asociado, si aplica", example = "4001")
    private Long receiptId;

    @Schema(description = "Identificador de la imagen del recibo asociado, si aplica", example = "4001")
    private Long receiptImageId;

    @Schema(description = "Monto de la transaccion", example = "150.75")
    private BigDecimal amount;

    @Schema(description = "Metodo de pago utilizado en la transaccion", implementation = PaymentMethod.class, example = "CREDIT_CARD")
    private PaymentMethod paymentMethod;

    @Schema(description = "Tipo de transaccion (ingreso o gasto)", implementation = TransactionType.class, example = "SPENT")
    private TransactionType type;

    @Schema(description = "Fecha y hora de creacion de la transaccion", example = "2026-06-25T23:59:59")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha y hora en que la transaccion fue deshabilitada, si aplica", example = "2026-07-01T12:00:00")
    private LocalDateTime disabledAt;

    @Schema(description = "Indica si la transaccion esta activa", example = "true")
    private boolean active;
}
