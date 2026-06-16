package com.elias.finanx.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransactionType {
    SPENT("Gasto"),
    INCOME("Ingreso");

    private final String displayName;
}
