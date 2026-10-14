package com.elias.mismonedas.planning.controller;

import com.elias.mismonedas.planning.dto.saving.SavingGoalRequest;
import com.elias.mismonedas.planning.dto.saving.SavingGoalResponse;
import com.elias.mismonedas.planning.service.SavingGoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RequestMapping("/savings")
@RestController
@RequiredArgsConstructor
public class SavingGoalController {

    private final SavingGoalService savingGoalService;

    @Operation(
            summary = "Listar metas activas",
            description = "Obtiene metas de ahorro activas por usuario.",
            operationId = "findAllActiveSavingGoalByUser",
            tags = {"Savings"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/user/{idUser}")
    public ResponseEntity<List<SavingGoalResponse>> findAllActiveByUser(
            @Parameter(description = "ID usuario") @PathVariable Long idUser) {
        return ResponseEntity.ok(savingGoalService.findAllActiveByUser(idUser));
    }

    @Operation(
            summary = "Obtener una meta de ahorro",
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
    public ResponseEntity<SavingGoalResponse> create(
            @Valid @RequestBody SavingGoalRequest request) {
        SavingGoalResponse saved = savingGoalService.create(request);
        return ResponseEntity.created(URI.create("/reasons/" + saved.getId())).body(saved);
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
