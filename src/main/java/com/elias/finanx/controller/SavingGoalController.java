package com.elias.finanx.controller;

import com.elias.finanx.dto.saving.SavingGoalRequest;
import com.elias.finanx.dto.saving.SavingGoalResponse;
import com.elias.finanx.service.ReasonService;
import com.elias.finanx.service.SavingGoalService;
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

    @GetMapping("/{idUser}")
    public ResponseEntity<List<SavingGoalResponse>> findAllActiveByUser(@PathVariable Long idUser) {
        return ResponseEntity.ok(savingGoalService.findAllActiveByUser(idUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SavingGoalResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(savingGoalService.findById(id));
    }

    @PostMapping
    public ResponseEntity<SavingGoalResponse> create(@Valid @RequestBody SavingGoalRequest request) {
        SavingGoalResponse saved = savingGoalService.create(request);
        return ResponseEntity.created(URI.create("/reasons/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavingGoalResponse> update(@PathVariable Long id, @Valid @RequestBody SavingGoalRequest request) {
        return ResponseEntity.ok(savingGoalService.update(id, request));
    }

    @PostMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        savingGoalService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
