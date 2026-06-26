package com.elias.finanx.controller;

import com.elias.finanx.dto.transaction.TransactionRequest;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    @Operation(
            summary = "Listar transacciones activas",
            description = "Obtiene todas las transacciones activas de un usuario.",
            operationId = "findAllActiveTransactionByUser",
            tags = {"Transactions"}
    )
    @GetMapping("/user/{userId}/active")
    public ResponseEntity<List<TransactionResponse>> findAllActiveByUser(
            @Parameter(description = "ID usuario") @PathVariable Long userId) {
        return ResponseEntity.ok(transactionService.findAllActiveByUser(userId));
    }

    @Operation(
            summary = "Buscar transaccion",
            description = "Obtiene una transaccion por ID.",
            operationId = "findTransactionById",
            tags = {"Transactions"}
    )
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.findById(id));
    }

    @Operation(
            summary = "Listar transacciones por usuario",
            description = "Obtiene todas las transacciones de un usuario.",
            operationId = "findAllTransactionByUser",
            tags = {"Transactions"}
    )
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionResponse>> findAllByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(transactionService.findAllByUser(userId));
    }

    @Operation(
            summary = "Listar transacciones por rango",
            description = "Obtiene transacciones en un rango de fechas.",
            operationId = "findAllTransactionsByUserBetween",
            tags = {"Transactions"}
    )
    @GetMapping("/user/{userId}/between")
    public ResponseEntity<List<TransactionResponse>> findAllByUserBetween(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(transactionService.findAllByUserAndIssueDateBetween(userId, from, to));
    }

    @Operation(
            summary = "Crear transaccion",
            description = "Crea una nueva transaccion.",
            operationId = "createTransaction",
            tags = {"Transactions"}
    )
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse saved = transactionService.create(request);
        return ResponseEntity.created(URI.create("/transactions/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Actualizar transaccion",
            description = "Actualiza una transaccion.",
            operationId = "updateTransaction",
            tags = {"Transactions"}
    )
    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.update(id, request));
    }

    @Operation(
            summary = "Eliminar transaccion",
            description = "Deshabilita una transaccion.",
            operationId = "transactionDelete",
            tags = {"Transactions"}
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        transactionService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Filtrar por categoria y fecha",
            description = "Obtiene transacciones por categoria y rango de fechas.",
            operationId = "findAllTransactionByCategoryAndDateBetween",
            tags = {"Transactions"}
    )
    @GetMapping("/user/{userId}/category/{categoryId}/between")
    public ResponseEntity<List<TransactionResponse>> findAllByCategoryAndDateBetween(
            @PathVariable Long userId,
            @PathVariable Long categoryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                transactionService.findAllActiveByCategoryAndDateBetween(userId, categoryId, from, to)
        );
    }
}
