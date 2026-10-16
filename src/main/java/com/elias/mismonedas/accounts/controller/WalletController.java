package com.elias.mismonedas.accounts.controller;

import com.elias.mismonedas.accounts.dto.wallet.WalletRequest;
import com.elias.mismonedas.accounts.dto.wallet.WalletResponse;
import com.elias.mismonedas.accounts.service.WalletService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Listar billeteras del usuario autenticado", description = "Lista paginada con page, size y sort estándar.", operationId = "listWallets", tags = {"Wallets"})
    @GetMapping
    public ResponseEntity<PageResponse<WalletResponse>> listWallets(
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(walletService.list(pageable));
    }

    @Operation(summary = "Obtener billetera por ID", tags = {"Wallets"})
    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.findById(id));
    }

    @Operation(summary = "Crear billetera", tags = {"Wallets"})
    @PostMapping
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody WalletRequest request) {
        WalletResponse saved = walletService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/wallets/" + saved.getId())).body(saved);
    }

    @Operation(summary = "Actualizar billetera", tags = {"Wallets"})
    @PutMapping("/{id}")
    public ResponseEntity<WalletResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody WalletRequest request) {
        return ResponseEntity.ok(walletService.update(id, request));
    }

    @Operation(summary = "Deshabilitar billetera", tags = {"Wallets"})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        walletService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
