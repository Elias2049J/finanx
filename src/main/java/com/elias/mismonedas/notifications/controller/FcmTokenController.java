package com.elias.mismonedas.notifications.controller;

import com.elias.mismonedas.identity.security.RequireOwnership;
import com.elias.mismonedas.notifications.dto.fcm.FcmTokenResponse;
import com.elias.mismonedas.notifications.dto.fcm.RegisterFcmTokenRequest;
import com.elias.mismonedas.notifications.service.FcmTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/users/{userId}/fcm-tokens")
@RequiredArgsConstructor
@Tag(name = "FCM Tokens", description = "Gestion de tokens FCM para notificaciones push")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @Operation(summary = "Registrar token FCM", operationId = "registerFcmToken")
    @PostMapping
    public ResponseEntity<FcmTokenResponse> register(
            @RequireOwnership @PathVariable Long userId,
            @Valid @RequestBody RegisterFcmTokenRequest request) {
        FcmTokenResponse saved = fcmTokenService.register(userId, request);
        return ResponseEntity.created(URI.create("/users/" + userId + "/fcm-tokens/" + saved.getId())).body(saved);
    }

    @Operation(summary = "Eliminar token FCM", operationId = "removeFcmToken")
    @DeleteMapping("/{tokenId}")
    public ResponseEntity<Void> remove(
            @RequireOwnership @PathVariable Long userId,
            @PathVariable Long tokenId) {
        fcmTokenService.remove(userId, tokenId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Listar tokens FCM del usuario", operationId = "listFcmTokens")
    @GetMapping
    public ResponseEntity<List<FcmTokenResponse>> list(@RequireOwnership @PathVariable Long userId) {
        return ResponseEntity.ok(fcmTokenService.findAllByUser(userId));
    }
}
