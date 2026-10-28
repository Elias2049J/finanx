package com.elias.mismonedas.scheduling.domain.event;

/**
 * Se publica cuando una ocurrencia manual vence y queda en
 * {@code PENDING_CONFIRMATION}. El listener de notificaciones lo consume
 * despues del commit para avisar al usuario una sola vez por ocurrencia.
 */
public record SchedulePendingConfirmationEvent(
        Long scheduleId,
        Long userId,
        String scheduleType) {
}
