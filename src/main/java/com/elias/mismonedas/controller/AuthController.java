package com.elias.mismonedas.controller;

import com.elias.mismonedas.adapter.auth.GoogleAuthService;
import com.elias.mismonedas.dto.auth.GoogleLoginRequest;
import com.elias.mismonedas.dto.auth.LoginRequest;
import com.elias.mismonedas.dto.auth.LoginResponse;
import com.elias.mismonedas.dto.user.UserRequest;
import com.elias.mismonedas.service.AuthService;
import com.elias.mismonedas.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final UserService userService;

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
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
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
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody UserRequest request) {
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
    public ResponseEntity<LoginResponse> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(googleAuthService.authenticate(request));
    }
}