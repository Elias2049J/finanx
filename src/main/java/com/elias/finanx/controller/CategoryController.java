package com.elias.finanx.controller;

import com.elias.finanx.dto.category.CategoryRequest;
import com.elias.finanx.dto.category.CategoryResponse;
import com.elias.finanx.service.CategoryService;
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

    @GetMapping("/{idUser}")
    public ResponseEntity<List<CategoryResponse>> findAllByUser(@PathVariable Long idUser) {
        return ResponseEntity.ok(categoryService.findAllByUser(idUser));
    }

    @GetMapping("/{idUser}/{active}")
    public ResponseEntity<List<CategoryResponse>> findAllByUserAndActive(
            @PathVariable Long idUser,
            @PathVariable boolean active) {
        return ResponseEntity.ok(categoryService.findAllByUserAndActive(idUser, active));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse saved = categoryService.create(request);
        return ResponseEntity.created(URI.create("/categories/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @PostMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        categoryService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}")
    public ResponseEntity<CategoryResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.activate(id));
    }
}
