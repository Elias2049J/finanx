package com.elias.finanx.controller;

import com.elias.finanx.dto.user.UserPreferencesRequest;
import com.elias.finanx.dto.user.UserPreferencesResponse;
import com.elias.finanx.service.UserPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/{userId}/preferences")
@RequiredArgsConstructor
@Tag(name = "User Preferences", description = "Gestion de preferencias de usuario")
public class UserPreferencesController {

    private final UserPreferencesService preferencesService;

    @Operation(summary = "Obtener preferencias del usuario", operationId = "getUserPreferences")
    @GetMapping
    public ResponseEntity<UserPreferencesResponse> get(@PathVariable Long userId) {
        return ResponseEntity.ok(preferencesService.findByUserId(userId));
    }

    @Operation(summary = "Actualizar preferencias del usuario", operationId = "updateUserPreferences")
    @PutMapping
    public ResponseEntity<UserPreferencesResponse> update(
            @PathVariable Long userId,
            @Valid @RequestBody UserPreferencesRequest request) {
        return ResponseEntity.ok(preferencesService.update(userId, request));
    }
}
