package com.elias.finanx.controller;

import com.elias.finanx.dto.budget.BudgetRequest;
import com.elias.finanx.dto.budget.BudgetResponse;
import com.elias.finanx.entity.enums.BudgetState;
import com.elias.finanx.service.BudgetService;
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

    @GetMapping("/user/{idUser}")
    public ResponseEntity<List<BudgetResponse>> findAllByUser(@PathVariable Long idUser) {
        return ResponseEntity.ok(budgetService.findAllByUser(idUser));
    }

    @GetMapping("/user/{idUser}/{state}")
    public ResponseEntity<List<BudgetResponse>> findAllByUserAndState(
            @PathVariable Long idUser,
            @PathVariable BudgetState state
            ) {
        return ResponseEntity.ok(budgetService.findAllActiveByUserAndState(idUser, state));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(budgetService.findById(id));
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody BudgetRequest request) {
        BudgetResponse saved = budgetService.create(request);
        return ResponseEntity.created(URI.create("/reasons/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponse> update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.ok(budgetService.update(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        budgetService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        budgetService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
