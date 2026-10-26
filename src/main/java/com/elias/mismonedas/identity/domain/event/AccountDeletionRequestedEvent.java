package com.elias.mismonedas.identity.domain.event;

public record AccountDeletionRequestedEvent(Long userId, java.time.OffsetDateTime scheduledAt) {
}
