package com.elias.mismonedas.notifications.controller;

import com.elias.mismonedas.notifications.dto.fcm.FcmTokenResponse;
import com.elias.mismonedas.notifications.dto.fcm.RegisterFcmTokenRequest;
import com.elias.mismonedas.notifications.service.FcmTokenService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fcm-tokens")
@RequiredArgsConstructor
@Tag(name = "FCM Tokens", description = "Gestion de tokens FCM para notificaciones push")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @Operation(summary = "Registrar token FCM", operationId = "registerFcmToken")
    @PostMapping
    public ResponseEntity<FcmTokenResponse> register(@Valid @RequestBody RegisterFcmTokenRequest request) {
        FcmTokenResponse saved = fcmTokenService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/fcm-tokens/" + saved.getId())).body(saved);
    }

    @Operation(summary = "Eliminar token FCM", operationId = "removeFcmToken")
    @DeleteMapping("/{tokenId}")
    public ResponseEntity<Void> remove(@PathVariable Long tokenId) {
        fcmTokenService.remove(tokenId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Listar tokens FCM del usuario", description = "Lista paginada con page, size y sort estándar.", operationId = "listFcmTokens")
    @GetMapping
    public ResponseEntity<PageResponse<FcmTokenResponse>> listFcmTokens(
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(fcmTokenService.list(pageable));
    }
}
