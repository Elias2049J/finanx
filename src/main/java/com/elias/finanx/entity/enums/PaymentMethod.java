package com.elias.finanx.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PaymentMethod {
    CASH("Efectivo"),
    CREDIT_CARD("Trj. Crédito"),
    DEBIT_CARD("Trj. Débito"),
    BANK_TRANSFER("Transferencia"),
    YAPE("Yape"),
    PLIN("Plin"),
    OTHER("Otro");

    private final String displayName;
}
