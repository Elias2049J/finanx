package com.elias.finanx.entity.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Getter
@AllArgsConstructor
public enum NotificationType {
    BUDGET_ALERT("Alerta de presupuesto"),
    EXPIRATION("Vencimiento"),
    REMINDER("Recordatorio");

    private final String displayName;
}
