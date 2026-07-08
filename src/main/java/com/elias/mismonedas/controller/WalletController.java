package com.elias.mismonedas.controller;

import com.elias.mismonedas.dto.wallet.WalletRequest;
import com.elias.mismonedas.dto.wallet.WalletResponse;
import com.elias.mismonedas.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/users/{userId}/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Listar billeteras de un usuario", tags = {"Wallets"})
    @GetMapping
    public ResponseEntity<List<WalletResponse>> findAllByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(walletService.findAllByUser(userId));
    }

    @Operation(summary = "Obtener billetera por ID", tags = {"Wallets"})
    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.findById(id));
    }

    @Operation(summary = "Crear billetera", tags = {"Wallets"})
    @PostMapping
    public ResponseEntity<WalletResponse> create(
            @PathVariable Long userId,
            @Valid @RequestBody WalletRequest request) {
        WalletResponse saved = walletService.create(userId, request);
        return ResponseEntity.created(URI.create("/users/" + userId + "/wallets/" + saved.getId()))
                .body(saved);
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
