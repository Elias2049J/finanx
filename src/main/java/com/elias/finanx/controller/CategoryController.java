package com.elias.finanx.controller;

import com.elias.finanx.dto.category.CategoryRequest;
import com.elias.finanx.dto.category.CategoryResponse;
import com.elias.finanx.service.CategoryService;
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
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(
            summary = "Listar categorías por usuario",
            description = "Obtiene todas las categorías asociadas a un usuario.",
            operationId = "categoryFindAllByUser",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping("/user/{idUser}")
    public ResponseEntity<List<CategoryResponse>> findAllByUser(
            @Parameter(description = "ID del usuario") @PathVariable Long idUser) {
        return ResponseEntity.ok(categoryService.findAllByUser(idUser));
    }

    @Operation(
            summary = "Listar categorías por usuario y estado",
            description = "Obtiene categorías filtradas por estado activo/inactivo.",
            operationId = "categoryFindAllByUserAndActive",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/user/{idUser}/{active}")
    public ResponseEntity<List<CategoryResponse>> findAllByUserAndActive(
            @Parameter(description = "ID del usuario") @PathVariable Long idUser,
            @Parameter(description = "Estado activo") @PathVariable boolean active) {
        return ResponseEntity.ok(categoryService.findAllByUserAndActive(idUser, active));
    }

    @Operation(
            summary = "Buscar categoría por ID",
            description = "Obtiene una categoría específica.",
            operationId = "categoryFindById",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Categoría encontrada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(
            @Parameter(description = "ID de la categoría") @PathVariable Long id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    @Operation(
            summary = "Crear categoría",
            description = "Crea una nueva categoría.",
            operationId = "categoryCreate",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Categoría creada"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos")
            }
    )
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Parameter(description = "Datos de la categoría") @Valid @RequestBody CategoryRequest request) {
        CategoryResponse saved = categoryService.create(request);
        return ResponseEntity.created(URI.create("/categories/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Actualizar categoría",
            description = "Actualiza una categoría existente.",
            operationId = "categoryUpdate",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Actualizada"),
                    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @Parameter(description = "ID de la categoría") @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @Operation(
            summary = "Deshabilitar categoría",
            description = "Deshabilita una categoría.",
            operationId = "categoryDisable",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deshabilitada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(
            @Parameter(description = "ID de la categoría") @PathVariable Long id) {
        categoryService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
