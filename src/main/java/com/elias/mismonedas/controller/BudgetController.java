package com.elias.mismonedas.controller;

import com.elias.mismonedas.dto.analytics.component.TimeBoundList;
import com.elias.mismonedas.dto.date.PeriodRequest;
import com.elias.mismonedas.dto.budget.BudgetRequest;
import com.elias.mismonedas.dto.budget.BudgetResponse;
import com.elias.mismonedas.entity.enums.BudgetHealth;
import com.elias.mismonedas.entity.enums.BudgetState;
import com.elias.mismonedas.service.BudgetService;
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
@RequestMapping("/budgets")
@RequiredArgsConstructor
public class BudgetController {
    private final BudgetService budgetService;

    @Operation(
            summary = "Listar presupuestos por usuario",
            description = "Obtiene todos los presupuestos de un usuario.",
            operationId = "findAllBudgetsByUser",
            tags = {"Budgets"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping("/user/{idUser}")
    public ResponseEntity<List<BudgetResponse>> findAllByUser(
            @Parameter(description = "ID usuario") @PathVariable Long idUser) {
        return ResponseEntity.ok(budgetService.findAllByUser(idUser));
    }

    @Operation(
            summary = "Listar por usuario y estado",
            description = "Obtiene presupuestos filtrados por estado.",
            operationId = "findAllBudgetsByUserAndState",
            tags = {"Budgets"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/user/{idUser}/{state}")
    public ResponseEntity<List<BudgetResponse>> findAllByUserAndState(
            @PathVariable Long idUser,
            @PathVariable BudgetState state) {
        return ResponseEntity.ok(budgetService.findAllActiveByUserAndState(idUser, state));
    }

    @Operation(
            summary = "Buscar presupuesto",
            description = "Obtiene un presupuesto por ID.",
            operationId = "findBudgetById",
            tags = {"Budgets"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Encontrado"),
                    @ApiResponse(responseCode = "404", description = "No encontrado")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(budgetService.findById(id));
    }

    @Operation(
            summary = "Crear presupuesto",
            description = "Crea un nuevo presupuesto.",
            operationId = "createBudget",
            tags = {"Budgets"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Creado"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos")
            }
    )
    @PostMapping
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody BudgetRequest request) {
        BudgetResponse saved = budgetService.create(request);
        return ResponseEntity.created(URI.create("/reasons/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Actualizar presupuesto",
            description = "Actualiza un presupuesto.",
            operationId = "updateBudget",
            tags = {"Budgets"})
    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponse> update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.ok(budgetService.update(id, request));
    }

    @Operation(
            summary = "Cancelar presupuesto",
            description = "Cancela un presupuesto.",
            operationId = "cancelBudget",
            tags = {"Budgets"})
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        budgetService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Deshabilitar presupuesto",
            description = "Deshabilita un presupuesto.",
            operationId = "disableBudget",
            tags = {"Budgets"})
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        budgetService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Filtrar por salud",
            description = "Filtra presupuestos por health.",
            operationId = "findAllBudgetsByPeriodAndHealth",
            tags = {"Budgets"})
    @PostMapping("/{health}")
    public ResponseEntity<TimeBoundList<BudgetResponse>> getBudgetsByHealth(
            @PathVariable BudgetHealth health,
            @Valid @RequestBody PeriodRequest request) {
        return ResponseEntity.ok(budgetService.getBudgetsByHealth(request, health));
    }
}