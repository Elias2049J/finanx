package com.elias.mismonedas.catalog.controller;

import com.elias.mismonedas.catalog.dto.category.CategoryRequest;
import com.elias.mismonedas.catalog.dto.category.CategoryResponse;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.catalog.service.CategoryService;
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

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(
            summary = "Listar categorias del usuario",
            description = "Lista paginada del usuario autenticado con filtros opcionales active, type y search.",
            operationId = "listCategories",
            tags = {"Categories"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido"),
                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<CategoryResponse>> listCategories(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(categoryService.list(active, type, search, pageable));
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
        return ResponseEntity.created(URI.create("/api/v1/categories/" + saved.getId())).body(saved);
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
