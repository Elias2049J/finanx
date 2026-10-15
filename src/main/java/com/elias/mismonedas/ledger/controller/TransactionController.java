package com.elias.mismonedas.ledger.controller;

import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final SecurityService securityService;

    @Operation(
            summary = "Listar transacciones activas",
            description = "Obtiene todas las transacciones activas del usuario autenticado.",
            operationId = "findAllActiveTransactions",
            tags = {"Transactions"}
    )
    @GetMapping("/me/active")
    public ResponseEntity<List<TransactionResponse>> findAllActiveByUser() {
        return ResponseEntity.ok(transactionService.findAllActiveByUser(securityService.getCurrentUser().getId()));
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
            summary = "Listar transacciones del usuario",
            description = "Obtiene todas las transacciones del usuario autenticado.",
            operationId = "findAllTransactions",
            tags = {"Transactions"}
    )
    @GetMapping("/me")
    public ResponseEntity<List<TransactionResponse>> findAllByUser() {
        return ResponseEntity.ok(transactionService.findAllByUser(securityService.getCurrentUser().getId()));
    }

    @Operation(
            summary = "Listar transacciones por rango",
            description = "Obtiene transacciones en un rango de fechas.",
            operationId = "findAllTransactionsBetween",
            tags = {"Transactions"}
    )
    @GetMapping("/me/between")
    public ResponseEntity<List<TransactionResponse>> findAllByUserBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(transactionService.findAllByUserAndIssueDateBetween(
                securityService.getCurrentUser().getId(), from, to));
    }

    @Operation(
            summary = "Crear transaccion",
            description = "Crea una nueva transaccion.",
            operationId = "createTransaction",
            tags = {"Transactions"}
    )
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) throws HttpException, IOException {
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
    public ResponseEntity<Void> disable(@PathVariable Long id) throws HttpException, IOException {
        transactionService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Filtrar por categoria y fecha",
            description = "Obtiene transacciones por categoria y rango de fechas.",
            operationId = "findAllTransactionsByCategoryAndDateBetween",
            tags = {"Transactions"}
    )
    @GetMapping("/me/category/{categoryId}/between")
    public ResponseEntity<List<TransactionResponse>> findAllByCategoryAndDateBetween(
            @PathVariable Long categoryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(transactionService.findAllActiveByCategoryAndDateBetween(
                securityService.getCurrentUser().getId(), categoryId, from, to));
    }
}
