package com.elias.mismonedas.identity.controller;

import com.elias.mismonedas.identity.dto.user.UserPreferencesRequest;
import com.elias.mismonedas.identity.dto.user.UserPreferencesResponse;
import com.elias.mismonedas.identity.service.UserPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/users/me/preferences")
@RequiredArgsConstructor
@Tag(name = "User Preferences", description = "Gestion de preferencias de usuario")
public class UserPreferencesController {

    private final UserPreferencesService preferencesService;

    @Operation(summary = "Obtener preferencias del usuario", operationId = "getUserPreferences")
    @GetMapping
    public ResponseEntity<UserPreferencesResponse> get() {
        return ResponseEntity.ok(preferencesService.findByUserId());
    }

    @Operation(summary = "Crear preferencias del usuario", operationId = "createUserPreferences")
    @PostMapping
    public ResponseEntity<UserPreferencesResponse> create(@Valid @RequestBody UserPreferencesRequest request) {
        UserPreferencesResponse saved = preferencesService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/me/preferences")).body(saved);
    }

    @Operation(summary = "Actualizar preferencias del usuario", operationId = "updateUserPreferences")
    @PutMapping
    public ResponseEntity<UserPreferencesResponse> update(@Valid @RequestBody UserPreferencesRequest request) {
        return ResponseEntity.ok(preferencesService.update(request));
    }
}
