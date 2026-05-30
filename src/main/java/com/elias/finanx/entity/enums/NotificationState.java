package com.elias.finanx.entity.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Getter
@AllArgsConstructor
public enum NotificationState {
    SCHEDULED("Programado"),
    SENT("Enviado"),
    READ("Leído"),
    DISCARD("Descartado");

    private final String displayName;

    public boolean isSent() {
        return this == SENT;
    }

    public boolean isCreated() {
        return this == SCHEDULED;
    }

    public boolean isRead() {
        return this == READ;
    }

    public boolean isDiscarded() {
        return this == DISCARD;
    }
}