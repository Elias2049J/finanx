package com.elias.finanx.entity.enums;

public enum BudgetState {
    ACTIVE,
    CANCELLED,
    DISABLED,
    FINALIZED;
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
