package com.elias.mismonedas.scheduling.controller;

import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.schedule.ScheduleRequest;
import com.elias.mismonedas.scheduling.dto.schedule.ScheduleResponse;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(
            summary = "Crear programacion",
            description = "Crea una nueva programacion.",
            operationId = "createSchedule",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Programacion creada",
                            content = @Content(schema = @Schema(implementation = ScheduleResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos")
            }
    )
    @PostMapping
    public ResponseEntity<ScheduleResponse> create(
            @Parameter(description = "Datos de la programacion")
            @Valid @RequestBody ScheduleRequest request) {
        ScheduleResponse saved = scheduleService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/schedule/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Actualizar programacion",
            description = "Actualiza una programacion existente.",
            operationId = "updateSchedule",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Actualizada",
                            content = @Content(schema = @Schema(implementation = ScheduleResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PutMapping("/{id}")
    public ResponseEntity<ScheduleResponse> update(
            @Parameter(description = "ID de la programacion")
            @PathVariable Long id,
            @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.update(id, request));
    }

    @Operation(
            summary = "Listar programaciones por usuario y estado",
            description = "Lista programaciones activas paginadas con filtro opcional state.",
            operationId = "listSchedules",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<ScheduleResponse>> listSchedules(
            @RequestParam(required = false) ScheduleState state,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "nextRunAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(scheduleService.list(state, pageable));
    }

    @Operation(
            summary = "Pausar programacion",
            description = "Pausa una programacion.",
            operationId = "pauseSchedule",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Pausada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pause(
            @Parameter(description = "ID de la programacion") @PathVariable Long id) {
        scheduleService.pause(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Reanudar programacion",
            description = "Reanuda una programacion pausada.",
            operationId = "resumeSchedule",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Reanudada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resume(
            @Parameter(description = "ID de la programacion") @PathVariable Long id) {
        scheduleService.resume(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Cancelar programacion",
            description = "Cancela una programacion.",
            operationId = "cancelSchedule",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Cancelada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @Parameter(description = "ID de la programacion") @PathVariable Long id) {
        scheduleService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Deshabilitar programacion",
            description = "Deshabilita una programacion.",
            operationId = "disableSchedule",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deshabilitada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @DeleteMapping("/{id}/disable")
    public ResponseEntity<Void> disable(
            @Parameter(description = "ID de la programacion") @PathVariable Long id) {
        scheduleService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
