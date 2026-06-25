package com.elias.finanx.controller;

import com.elias.finanx.dto.schedule.ScheduleRequest;
import com.elias.finanx.dto.schedule.ScheduleResponse;
import com.elias.finanx.entity.enums.ScheduleState;
import com.elias.finanx.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(
            summary = "Crear programación",
            description = "Crea una nueva programación.",
            operationId = "scheduleCreate",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Programación creada"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos")
            }
    )
    @PostMapping
    public ResponseEntity<ScheduleResponse> create(
            @Parameter(description = "Datos de la programación")
            @Valid @RequestBody ScheduleRequest request) {
        ScheduleResponse saved = scheduleService.create(request);
        return ResponseEntity.created(URI.create("/schedule/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Actualizar programación",
            description = "Actualiza una programación existente.",
            operationId = "scheduleUpdate",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Actualizada"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PutMapping("/{id}")
    public ResponseEntity<ScheduleResponse> update(
            @Parameter(description = "ID de la programación")
            @PathVariable Long id,
            @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.update(id, request));
    }

    @Operation(
            summary = "Listar programaciones por usuario y estado",
            description = "Obtiene programaciones activas filtradas por usuario y estado.",
            operationId = "scheduleFindByUserAndState",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/user/{idUser}/{state}")
    public ResponseEntity<List<ScheduleResponse>> findAllByUserAndState(
            @Parameter(description = "ID usuario") @PathVariable Long idUser,
            @Parameter(description = "Estado de la programación") @PathVariable ScheduleState state) {
        return ResponseEntity.ok(scheduleService.findAllActiveByUserAndState(idUser, state));
    }

    @Operation(
            summary = "Pausar programación",
            description = "Pausa una programación.",
            operationId = "schedulePause",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Pausada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pause(
            @Parameter(description = "ID de la programación") @PathVariable Long id) {
        scheduleService.pause(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Reanudar programación",
            description = "Reanuda una programación pausada.",
            operationId = "scheduleResume",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Reanudada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resume(
            @Parameter(description = "ID de la programación") @PathVariable Long id) {
        scheduleService.resume(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Cancelar programación",
            description = "Cancela una programación.",
            operationId = "scheduleCancel",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Cancelada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @Parameter(description = "ID de la programación") @PathVariable Long id) {
        scheduleService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Deshabilitar programación",
            description = "Deshabilita una programación.",
            operationId = "scheduleDisable",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deshabilitada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @DeleteMapping("/{id}/disable")
    public ResponseEntity<Void> disable(
            @Parameter(description = "ID de la programación") @PathVariable Long id) {
        scheduleService.disable(id);
        return ResponseEntity.noContent().build();
    }
}