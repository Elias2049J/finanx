package com.elias.finanx.controller;

import com.elias.finanx.dto.transaction.TransactionRequest;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<List<TransactionResponse>> findAllActiveByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(transactionService.findAllActiveByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.findById(id));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<TransactionResponse>> findAllByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(transactionService.findAllByUser(userId));
    }

    @GetMapping("/user/{userId}/between")
    public ResponseEntity<List<TransactionResponse>> findAllByUserBetween(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(transactionService.findAllByUserAndIssueDateBetween(userId, from, to));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request) {
        TransactionResponse saved = transactionService.create(request);
        return ResponseEntity.created(URI.create("/transactions/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        transactionService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
