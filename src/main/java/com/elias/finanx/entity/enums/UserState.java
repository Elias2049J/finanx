package com.elias.finanx.entity.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Getter
@AllArgsConstructor
public enum UserState {
    ENABLED("Habilitado"),
    BLOCKED("Bloqueado"),
    DISABLED("Deshabilitado");

    private final String displayName;

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
