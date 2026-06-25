package com.elias.finanx.controller;

import com.elias.finanx.dto.notification.NotificationDTO;
import com.elias.finanx.entity.enums.NotificationState;
import com.elias.finanx.service.NotificationService;
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

    @Operation(
            summary = "Listar notificaciones por usuario",
            description = "Obtiene todas las notificaciones de un usuario.",
            operationId = "notificationFindAllByUser",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping("/user/{idUser}")
    public ResponseEntity<List<NotificationDTO>> findAllByUser(
            @Parameter(description = "ID usuario") @PathVariable Long idUser) {
        return ResponseEntity.ok(notificationService.findAllByUser(idUser));
    }

    @Operation(
            summary = "Buscar notificación",
            description = "Obtiene una notificación por ID.",
            operationId = "notificationFindById",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Encontrada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<NotificationDTO> findById(
            @Parameter(description = "ID notificación") @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.findById(id));
    }

    @Operation(
            summary = "Listar por estado",
            description = "Obtiene notificaciones filtradas por estado.",
            operationId = "notificationFindByUserAndState",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/user/{idUser}/{state}")
    public ResponseEntity<List<NotificationDTO>> findAllByUserAndState(
            @Parameter(description = "ID usuario") @PathVariable Long idUser,
            @Parameter(description = "Estado") @PathVariable NotificationState state) {
        return ResponseEntity.ok(notificationService.findAllByUserAndState(idUser, state));
    }

    @Operation(
            summary = "Descartar notificación",
            description = "Marca una notificación como descartada.",
            operationId = "notificationDiscard",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Descartada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/discard")
    public ResponseEntity<Void> discard(
            @Parameter(description = "ID de la notificación") @RequestBody Long idNotification) {
        notificationService.discard(idNotification);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Marcar como leída",
            description = "Marca una notificación como leída.",
            operationId = "notificationMarkAsRead",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Actualizada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/mark-as-read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(description = "ID de la notificación") @RequestBody Long idNotification) {
        notificationService.markAsRead(idNotification);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Eliminar notificaciones antiguas",
            description = "Elimina notificaciones anteriores a una fecha.",
            operationId = "notificationPurge",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Eliminadas")
            }
    )
    @PostMapping("/user/{userId}/purge-older-than")
    public ResponseEntity<Void> purge(
            @Parameter(description = "ID usuario") @PathVariable Long userId,
            @Parameter(description = "Fecha límite")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime) {
        notificationService.purgeOlderThan(dateTime, userId);
        return ResponseEntity.noContent().build();
    }
}