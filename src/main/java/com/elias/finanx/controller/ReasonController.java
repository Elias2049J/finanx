package com.elias.finanx.controller;

import com.elias.finanx.dto.reason.ReasonRequest;
import com.elias.finanx.dto.reason.ReasonResponse;
import com.elias.finanx.service.ReasonService;
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

    @Operation(
            summary = "Listar razones por usuario",
            description = "Obtiene todas las razones asociadas a un usuario.",
            operationId = "findAllReasonByUser",
            tags = {"Reasons"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping("/user/{idUser}")
    public ResponseEntity<List<ReasonResponse>> findAllByUser(
            @Parameter(description = "ID del usuario") @PathVariable Long idUser) {
        return ResponseEntity.ok(reasonService.findAllByUser(idUser));
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