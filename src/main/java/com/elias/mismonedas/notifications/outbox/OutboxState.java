package com.elias.mismonedas.notifications.outbox;

public enum OutboxState {
    PENDING,
    PUBLISHED,
    SENT,
    FAILED,
    SKIPPED
}

