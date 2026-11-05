package com.elias.mismonedas.identity.controller;

import com.elias.mismonedas.identity.dto.auth.PasswordResetConfirmRequest;
import com.elias.mismonedas.identity.dto.auth.PasswordResetRequest;
import com.elias.mismonedas.identity.service.EmailVerificationChallengeManager;
import com.elias.mismonedas.identity.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @Operation(
            summary = "Solicitar restablecimiento de contrasena",
            description = "Envia un codigo de seis digitos si la cuenta local verificada existe. " +
                    "La respuesta siempre es 202 para no revelar si el correo esta registrado.",
            operationId = "requestPasswordReset",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "202", description = "Solicitud aceptada"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos")
            }
    )
    @PostMapping("/request")
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.accepted().build();
    }

    @Operation(
            summary = "Confirmar restablecimiento de contrasena",
            description = "Valida el codigo de seis digitos, cambia la contrasena y revoca todas las sesiones.",
            operationId = "confirmPasswordReset",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Contrasena actualizada"),
                    @ApiResponse(responseCode = "400", description = "Codigo invalido, expirado o limite de intentos alcanzado")
            }
    )
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        EmailVerificationChallengeManager.VerificationResult result =
                passwordResetService.confirmReset(
                        request.getEmail(), request.getCode(), request.getNewPassword());
        if (result == EmailVerificationChallengeManager.VerificationResult.VERIFIED) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.badRequest()
                .header("X-Password-Reset-Status", result.name())
                .build();
    }
}
