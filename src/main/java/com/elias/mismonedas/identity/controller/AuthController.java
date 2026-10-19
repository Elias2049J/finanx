package com.elias.mismonedas.identity.controller;

import com.elias.mismonedas.identity.adapter.auth.GoogleAuthService;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.auth.GoogleLoginRequest;
import com.elias.mismonedas.identity.dto.auth.LoginRequest;
import com.elias.mismonedas.identity.dto.auth.RefreshRequest;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.service.AuthService;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.UserService;
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
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final UserService userService;
    private final AuthTokenService authTokenService;

    @Operation(
            summary = "Login",
            description = "Autenticacion de usuario.",
            operationId = "login",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login exitoso"),
                    @ApiResponse(responseCode = "400", description = "Credenciales invalidas")
            }
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "Registro",
            description = "Registro de nuevo usuario.",
            operationId = "authRegister",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Registro exitoso"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos")
            }
    )
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @Operation(
            summary = "Login con Google",
            description = "Autenticacion mediante Google.",
            operationId = "authWithGoogle",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login exitoso"),
                    @ApiResponse(responseCode = "400", description = "Token invalido")
            }
    )
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(googleAuthService.authenticate(request));
    }

    @Operation(
            summary = "Refrescar tokens",
            description = "Rota el refresh token y emite un nuevo par de tokens.",
            operationId = "refreshToken",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tokens renovados"),
                    @ApiResponse(responseCode = "401", description = "Refresh token invalido")
            }
    )
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authTokenService.refresh(request.getRefreshToken()));
    }

    @Operation(
            summary = "Logout",
            description = "Revoca la sesion asociada al refresh token.",
            operationId = "logout",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Sesion revocada")
            }
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authTokenService.revoke(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}