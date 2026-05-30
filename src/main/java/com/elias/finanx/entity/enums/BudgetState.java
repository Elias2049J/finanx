package com.elias.finanx.entity.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Getter
@AllArgsConstructor
public enum BudgetState {
    ACTIVE("Activo"),
    CANCELLED("Cancelado"),
    FINALIZED("Finalizado");

    private final String displayName;

    public boolean isActive() {
        return this == ACTIVE;
    }
    public boolean isCancelled() {
        return this == ACTIVE;
    }
    public boolean isFinalized() {
        return this == ACTIVE;
    }
}
