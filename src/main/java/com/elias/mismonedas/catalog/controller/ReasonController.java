package com.elias.mismonedas.catalog.controller;

import com.elias.mismonedas.catalog.dto.reason.ReasonRequest;
import com.elias.mismonedas.catalog.dto.reason.ReasonResponse;
import com.elias.mismonedas.catalog.service.ReasonService;
import com.elias.mismonedas.identity.security.SecurityService;
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
@RequestMapping("/reasons")
@RequiredArgsConstructor
public class ReasonController {

    private final ReasonService reasonService;
    private final SecurityService securityService;

    @Operation(
            summary = "Listar razones del usuario",
            description = "Obtiene todas las razones del usuario autenticado.",
            operationId = "findAllReasons",
            tags = {"Reasons"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping("/me")
    public ResponseEntity<List<ReasonResponse>> findAllByUser() {
        return ResponseEntity.ok(reasonService.findAllByUser(securityService.getCurrentUser().getId()));
    }

    @Operation(
            summary = "Buscar razon",
            description = "Obtiene una razon por ID.",
            operationId = "findReasonById",
            tags = {"Reasons"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Encontrada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<ReasonResponse> findById(
            @Parameter(description = "ID de la razon") @PathVariable Long id) {
        return ResponseEntity.ok(reasonService.findById(id));
    }

    @Operation(
            summary = "Crear razon",
            description = "Crea una nueva razon.",
            operationId = "createReason",
            tags = {"Reasons"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Creada"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos")
            }
    )
    @PostMapping
    public ResponseEntity<ReasonResponse> create(
            @Parameter(description = "Datos de la razon") @Valid @RequestBody ReasonRequest request) {
        ReasonResponse saved = reasonService.create(request);
        return ResponseEntity.created(URI.create("/reasons/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Actualizar razon",
            description = "Actualiza una razon existente.",
            operationId = "updateReason",
            tags = {"Reasons"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Actualizada"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PutMapping("/{id}")
    public ResponseEntity<ReasonResponse> update(
            @Parameter(description = "ID de la razon") @PathVariable Long id,
            @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(reasonService.update(id, request));
    }

    @Operation(
            summary = "Deshabilitar razon",
            description = "Deshabilita una razon.",
            operationId = "disableReason",
            tags = {"Reasons"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deshabilitada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(
            @Parameter(description = "ID de la razon") @PathVariable Long id) {
        reasonService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
