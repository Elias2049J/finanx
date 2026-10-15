package com.elias.mismonedas.catalog.controller;

import com.elias.mismonedas.catalog.dto.category.CategoryRequest;
import com.elias.mismonedas.catalog.dto.category.CategoryResponse;
import com.elias.mismonedas.catalog.service.CategoryService;
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
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final SecurityService securityService;

    @Operation(
            summary = "Listar categorias del usuario",
            description = "Obtiene todas las categorias del usuario autenticado.",
            operationId = "findAllCategories",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping("/me")
    public ResponseEntity<List<CategoryResponse>> findAllByUser() {
        return ResponseEntity.ok(categoryService.findAllByUser(securityService.getCurrentUser().getId()));
    }

    @Operation(
            summary = "Listar categorias por estado",
            description = "Obtiene categorias del usuario autenticado filtradas por estado activo/inactivo.",
            operationId = "findAllCategoriesByActive",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/me/{active}")
    public ResponseEntity<List<CategoryResponse>> findAllByUserAndActive(
            @Parameter(description = "Estado activo") @PathVariable boolean active) {
        return ResponseEntity.ok(categoryService.findAllByUserAndActive(
                securityService.getCurrentUser().getId(), active));
    }

    @Operation(
            summary = "Buscar categoria por ID",
            description = "Obtiene una categoria especifica.",
            operationId = "findCategoryById",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(
            @Parameter(description = "ID de la categoria") @PathVariable Long id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    @Operation(
            summary = "Crear categoria",
            description = "Crea una nueva categoria.",
            operationId = "createCategory",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Creada"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos")
            }
    )
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Parameter(description = "Datos de la categoria") @Valid @RequestBody CategoryRequest request) {
        CategoryResponse saved = categoryService.create(request);
        return ResponseEntity.created(URI.create("/categories/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Actualizar categoria",
            description = "Actualiza una categoria existente.",
            operationId = "updateCategory",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Actualizada"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @Parameter(description = "ID de la categoria") @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @Operation(
            summary = "Deshabilitar categoria",
            description = "Deshabilita una categoria.",
            operationId = "disableCategory",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deshabilitada"),
                    @ApiResponse(responseCode = "404", description = "No encontrada")
            }
    )
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(
            @Parameter(description = "ID de la categoria") @PathVariable Long id) {
        categoryService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
