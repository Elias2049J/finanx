package com.elias.finanx.entity.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Getter
@AllArgsConstructor
public enum PaymentMethod {
    CASH("Efectivo"),
    CREDIT_CARD("Tarjeta de crédito"),
    DEBIT_CARD("Tarjeta de débito"),
    BANK_TRANSFER("Transferencia bancaria"),
    YAPE("Yape"),
    PLIN("Plin"),
    OTHER("Otro");

    private final String displayName;
}
