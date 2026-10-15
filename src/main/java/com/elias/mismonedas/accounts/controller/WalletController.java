package com.elias.mismonedas.accounts.controller;

import com.elias.mismonedas.accounts.dto.wallet.WalletRequest;
import com.elias.mismonedas.accounts.dto.wallet.WalletResponse;
import com.elias.mismonedas.accounts.service.WalletService;
import com.elias.mismonedas.identity.security.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/users/me/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final SecurityService securityService;

    @Operation(summary = "Listar billeteras del usuario autenticado", tags = {"Wallets"})
    @GetMapping
    public ResponseEntity<List<WalletResponse>> findAllByUser() {
        return ResponseEntity.ok(walletService.findAllByUser(securityService.getCurrentUser().getId()));
    }

    @Operation(summary = "Obtener billetera por ID", tags = {"Wallets"})
    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.findById(id));
    }

    @Operation(summary = "Crear billetera", tags = {"Wallets"})
    @PostMapping
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody WalletRequest request) {
        Long userId = securityService.getCurrentUser().getId();
        WalletResponse saved = walletService.create(userId, request);
        return ResponseEntity.created(URI.create("/users/me/wallets/" + saved.getId())).body(saved);
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
