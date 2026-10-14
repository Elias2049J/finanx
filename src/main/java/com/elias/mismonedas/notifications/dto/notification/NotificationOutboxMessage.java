package com.elias.mismonedas.notifications.dto.notification;

/** Queue envelope; full delivery data remains in the persisted outbox row. */
public record NotificationOutboxMessage(Long outboxId) {
}
