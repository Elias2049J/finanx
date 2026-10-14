package com.elias.mismonedas.notifications.outbox;

public enum NotificationDeliveryState {
    PENDING,
    SENT,
    SKIPPED,
    FAILED
}
