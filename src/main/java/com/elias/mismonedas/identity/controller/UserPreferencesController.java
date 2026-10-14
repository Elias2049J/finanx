package com.elias.mismonedas.identity.controller;

import com.elias.mismonedas.identity.dto.user.UserPreferencesRequest;
import com.elias.mismonedas.identity.dto.user.UserPreferencesResponse;
import com.elias.mismonedas.identity.security.RequireOwnership;
import com.elias.mismonedas.identity.service.UserPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/users/{userId}/preferences")
@RequiredArgsConstructor
@Tag(name = "User Preferences", description = "Gestion de preferencias de usuario")
public class UserPreferencesController {

    private final UserPreferencesService preferencesService;

    @Operation(summary = "Obtener preferencias del usuario", operationId = "getUserPreferences")
    @GetMapping
    public ResponseEntity<UserPreferencesResponse> get(@RequireOwnership @PathVariable Long userId) {
        return ResponseEntity.ok(preferencesService.findByUserId(userId));
    }

    @Operation(summary = "Crear preferencias del usuario", operationId = "createUserPreferences")
    @PostMapping
    public ResponseEntity<UserPreferencesResponse> create(
            @RequireOwnership @PathVariable Long userId,
            @Valid @RequestBody UserPreferencesRequest request) {
        UserPreferencesResponse saved = preferencesService.create(userId, request);
        return ResponseEntity.created(URI.create("/users/" + userId + "/preferences")).body(saved);
    }

    @Operation(summary = "Actualizar preferencias del usuario", operationId = "updateUserPreferences")
    @PutMapping
    public ResponseEntity<UserPreferencesResponse> update(
            @RequireOwnership @PathVariable Long userId,
            @Valid @RequestBody UserPreferencesRequest request) {
        return ResponseEntity.ok(preferencesService.update(userId, request));
    }
}
