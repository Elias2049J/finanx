package com.elias.mismonedas.notifications.outbox;

public enum NotificationDeliveryState {
    PENDING,
    SENDING,
    SENT,
    SKIPPED,
    FAILED
}
