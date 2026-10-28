package com.elias.mismonedas.scheduling.controller;

import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceConfirmationResponse;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceKeyRequest;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceResponse;
import com.elias.mismonedas.scheduling.dto.schedule.ScheduleRequest;
import com.elias.mismonedas.scheduling.dto.schedule.ScheduleResponse;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
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
            summary = "Listar ocurrencias de recordatorios",
            description = "Proyecta las ocurrencias pendientes y futuras de los recordatorios activos del usuario entre dos fechas, en su zona horaria.",
            operationId = "listScheduleOccurrences",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Ocurrencias proyectadas",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = OccurrenceResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "Rango de fechas invalido")
            }
    )
    @GetMapping("/occurrences")
    public ResponseEntity<List<OccurrenceResponse>> listOccurrences(
            @Parameter(description = "Fecha inicial inclusive", example = "2026-10-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha final inclusive", example = "2026-11-15")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(scheduleService.listOccurrences(from, to));
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
            summary = "Confirmar ocurrencia pendiente",
            description = "Materializa la ocurrencia pendiente de un recordatorio manual.",
            operationId = "confirmScheduleOccurrence",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Ocurrencia confirmada",
                            content = @Content(schema = @Schema(implementation = OccurrenceConfirmationResponse.class))),
                    @ApiResponse(responseCode = "409", description = "La ocurrencia ya no esta pendiente")
            }
    )
    @PostMapping("/{id}/confirm")
    public ResponseEntity<OccurrenceConfirmationResponse> confirm(
            @Parameter(description = "ID de la programacion") @PathVariable Long id,
            @Valid @RequestBody OccurrenceKeyRequest request) {
        return ResponseEntity.ok(scheduleService.confirm(id, request));
    }

    @Operation(
            summary = "Omitir ocurrencia pendiente",
            description = "Descarta la ocurrencia pendiente sin materializarla.",
            operationId = "ignoreScheduleOccurrence",
            tags = {"Schedule"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Ocurrencia omitida"),
                    @ApiResponse(responseCode = "409", description = "La ocurrencia ya no esta pendiente")
            }
    )
    @PostMapping("/{id}/ignore")
    public ResponseEntity<Void> ignore(
            @Parameter(description = "ID de la programacion") @PathVariable Long id,
            @Valid @RequestBody OccurrenceKeyRequest request) {
        scheduleService.ignore(id, request);
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
