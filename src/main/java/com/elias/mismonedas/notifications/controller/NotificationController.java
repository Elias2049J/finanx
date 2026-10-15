package com.elias.mismonedas.notifications.controller;

import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityService securityService;

    @Operation(
            summary = "Listar notificaciones del usuario",
            description = "Obtiene todas las notificaciones del usuario autenticado.",
            operationId = "findAllNotifications",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping("/me")
    public ResponseEntity<List<NotificationDTO>> findAllByUser() {
        return ResponseEntity.ok(notificationService.findAllByUser(securityService.getCurrentUser().getId()));
    }

    @Operation(
            summary = "Buscar notificacion",
            description = "Obtiene una notificacion por ID.",
            operationId = "findNotificationById",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Encontrada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<NotificationDTO> findById(
            @Parameter(description = "ID notificacion") @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.findById(id));
    }

    @Operation(
            summary = "Listar por estado",
            description = "Obtiene notificaciones del usuario autenticado filtradas por estado.",
            operationId = "findAllNotificationsByState",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/me/{state}")
    public ResponseEntity<List<NotificationDTO>> findAllByUserAndState(
            @Parameter(description = "Estado") @PathVariable NotificationState state) {
        return ResponseEntity.ok(notificationService.findAllByUserAndState(
                securityService.getCurrentUser().getId(), state));
    }

    @Operation(
            summary = "Descartar notificacion",
            description = "Marca una notificacion como descartada.",
            operationId = "discardNotification",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Descartada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/discard")
    public ResponseEntity<Void> discard(
            @Parameter(description = "ID de la notificacion") @RequestBody Long idNotification) {
        notificationService.discard(idNotification);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Marcar como leida",
            description = "Marca una notificacion como leida.",
            operationId = "markAsRead",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Actualizada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/mark-as-read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(description = "ID de la notificacion") @RequestBody Long idNotification) {
        notificationService.markAsRead(idNotification);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Eliminar notificaciones antiguas",
            description = "Elimina notificaciones anteriores a una fecha.",
            operationId = "purgeNotifications",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Eliminadas")
            }
    )
    @PostMapping("/me/purge-older-than")
    public ResponseEntity<Void> purge(
            @Parameter(description = "Fecha limite")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime) {
        notificationService.purgeOlderThan(dateTime, securityService.getCurrentUser().getId());
        return ResponseEntity.noContent().build();
    }
}
