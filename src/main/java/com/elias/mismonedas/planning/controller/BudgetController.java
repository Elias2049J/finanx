package com.elias.mismonedas.planning.controller;

import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import com.elias.mismonedas.planning.dto.budget.BudgetRequest;
import com.elias.mismonedas.planning.dto.budget.BudgetResponse;
import com.elias.mismonedas.planning.service.BudgetService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {
    private final BudgetService budgetService;

    @Operation(
            summary = "Listar presupuestos por usuario",
            description = "Lista paginada del usuario autenticado. Admite filtros active, state, health y rango from/to; active=true por defecto.",
            operationId = "listBudgets",
            tags = {"Budgets"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<BudgetResponse>> listBudgets(
            @RequestParam(defaultValue = "true") Boolean active,
            @RequestParam(required = false) BudgetState state,
            @RequestParam(required = false) BudgetHealth health,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(budgetService.list(
                active, state, health, from, to, pageable));
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
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + saved.getId())).body(saved);
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

}
