package com.elias.finanx.entity.enums;

public enum BudgetState {
    ACTIVE,
    CANCELLED,
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
