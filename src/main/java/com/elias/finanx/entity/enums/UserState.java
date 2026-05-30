package com.elias.finanx.entity.enums;

public enum UserState {
    ENABLED,
    BLOCKED,
    DISABLED;

    public boolean isEnabled() {
        return this == ENABLED;
    }

    public boolean isBlocked() {
        return this == BLOCKED;
    }

    public boolean isDisabled() {
        return this == DISABLED;
    }
}
