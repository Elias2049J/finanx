package com.elias.mismonedas.planning.controller;

import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import com.elias.mismonedas.planning.dto.saving.SavingGoalRequest;
import com.elias.mismonedas.planning.dto.saving.SavingGoalResponse;
import com.elias.mismonedas.planning.service.SavingGoalService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/savings")
@RequiredArgsConstructor
public class SavingGoalController {

    private final SavingGoalService savingGoalService;

    @Operation(
            summary = "Listar metas del usuario",
            description = "Obtiene las metas de ahorro del usuario autenticado.",
            operationId = "listSavingGoals",
            tags = {"Savings"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<SavingGoalResponse>> listSavingGoals(
            @RequestParam(defaultValue = "true") Boolean active,
            @RequestParam(required = false) SavingGoalState state,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(savingGoalService.list(
                active, state, pageable));
    }

    @Operation(
            summary = "Buscar meta",
            description = "Obtiene una meta de ahorro por ID.",
            operationId = "findSavingGoalById",
            tags = {"Savings"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Encontrada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<SavingGoalResponse> findById(
            @Parameter(description = "ID meta") @PathVariable Long id) {
        return ResponseEntity.ok(savingGoalService.findById(id));
    }

    @Operation(
            summary = "Crear meta",
            description = "Crea una nueva meta de ahorro.",
            operationId = "createSavingGoal",
            tags = {"Savings"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Creada"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos")
            }
    )
    @PostMapping
    public ResponseEntity<SavingGoalResponse> create(@Valid @RequestBody SavingGoalRequest request) {
        SavingGoalResponse saved = savingGoalService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/savings/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Actualizar meta",
            description = "Actualiza una meta de ahorro.",
            operationId = "updateSavingGoal",
            tags = {"Savings"}
    )
    @PutMapping("/{id}")
    public ResponseEntity<SavingGoalResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SavingGoalRequest request) {
        return ResponseEntity.ok(savingGoalService.update(id, request));
    }

    @Operation(
            summary = "Deshabilitar meta",
            description = "Deshabilita una meta.",
            operationId = "disableSavingGoal",
            tags = {"Savings"}
    )
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        savingGoalService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Cancelar meta",
            description = "Cancela una meta de ahorro.",
            operationId = "cancelSavingGoal",
            tags = {"Savings"}
    )
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        savingGoalService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
