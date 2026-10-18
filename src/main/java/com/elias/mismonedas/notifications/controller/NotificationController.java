package com.elias.mismonedas.notifications.controller;

import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "Listar notificaciones del usuario",
            description = "Lista paginada de notificaciones del usuario autenticado con filtros opcionales state y search.",
            operationId = "listNotifications",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<NotificationDTO>> listNotifications(
            @RequestParam(required = false) NotificationState state,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(notificationService.list(
                state, search, pageable));
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
            summary = "Descartar notificacion",
            description = "Marca una notificacion como descartada.",
            operationId = "discardNotification",
            tags = {"Notifications"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Descartada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/discard")
    public ResponseEntity<Void> discard(
            @Parameter(description = "ID de la notificacion") @PathVariable Long id) {
        notificationService.discard(id);
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
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(description = "ID de la notificacion") @PathVariable Long id) {
        notificationService.markAsRead(id);
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
    @PostMapping("/purge-older-than")
    public ResponseEntity<Void> purge(
            @Parameter(description = "Fecha limite")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime) {
        notificationService.purgeOlderThan(dateTime);
        return ResponseEntity.noContent().build();
    }
}
