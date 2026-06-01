package com.elias.finanx.controller;

import com.elias.finanx.dto.reason.ReasonRequest;
import com.elias.finanx.dto.reason.ReasonResponse;
import com.elias.finanx.service.ReasonService;
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

    @GetMapping("/{idUser}")
    public ResponseEntity<List<ReasonResponse>> findAll(@PathVariable Long idUser) {
        return ResponseEntity.ok(reasonService.findAllByUser(idUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReasonResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(reasonService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ReasonResponse> create(@Valid @RequestBody ReasonRequest request) {
        ReasonResponse saved = reasonService.create(request);
        return ResponseEntity.created(URI.create("/reasons/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReasonResponse> update(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(reasonService.update(id, request));
    }

    @PostMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        reasonService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
