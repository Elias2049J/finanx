package com.elias.mismonedas.notifications.controller;

import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/notifications/outbox")
@RequiredArgsConstructor
@Tag(name = "Notification operations", description = "Operaciones internas del outbox de notificaciones")
public class NotificationReplayController {

    private final NotificationDeliveryService deliveryService;

    @Operation(
            summary = "Reintentar una notificación fallida",
            description = "Reinicia solo entradas FAILED; el scheduler las publica. Si ya no está FAILED, no modifica la entrada.",
            operationId = "replayFailedNotification",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Replay aceptado o ya en curso"),
                    @ApiResponse(responseCode = "403", description = "Solo SYSTEM puede reintentar notificaciones"),
                    @ApiResponse(responseCode = "404", description = "Entrada de outbox no encontrada")
            }
    )
    @PostMapping("/{outboxId}/replay")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<Void> replay(@PathVariable Long outboxId) {
        deliveryService.replayFailed(outboxId);
        return ResponseEntity.accepted().build();
    }
}
