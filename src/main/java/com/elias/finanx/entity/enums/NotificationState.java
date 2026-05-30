package com.elias.finanx.entity.enums;

public enum NotificationState {
    SCHEDULED,
    SENT,
    READ,
    DISCARD;

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