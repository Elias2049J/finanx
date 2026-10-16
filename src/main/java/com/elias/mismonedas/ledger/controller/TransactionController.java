package com.elias.mismonedas.ledger.controller;

import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.service.TransactionService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.apache.http.HttpException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

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
            description = "Lista paginada del usuario autenticado. Admite filtros por active, type, walletId, categoryId y rango from/to; el orden usa sort estándar.",
            operationId = "listTransactions",
            tags = {"Transactions"}
    )
    @GetMapping
    public ResponseEntity<PageResponse<TransactionResponse>> listTransactions(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long walletId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(transactionService.list(
                active, type, walletId, categoryId, from, to, pageable));
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
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + saved.getId())).body(saved);
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

}
