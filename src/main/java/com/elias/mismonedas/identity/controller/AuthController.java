package com.elias.mismonedas.identity.controller;

import com.elias.mismonedas.identity.adapter.auth.GoogleAuthService;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.auth.RegisterVerificationRequest;
import com.elias.mismonedas.identity.dto.auth.GoogleLoginRequest;
import com.elias.mismonedas.identity.dto.auth.LoginRequest;
import com.elias.mismonedas.identity.dto.auth.LogoutRequest;
import com.elias.mismonedas.identity.dto.auth.RefreshRequest;
import com.elias.mismonedas.identity.domain.event.UserLoggedOutEvent;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.dto.auth.EmailVerificationResendRequest;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.service.EmailVerificationChallengeManager;
import com.elias.mismonedas.identity.service.EmailVerificationService;
import com.elias.mismonedas.identity.service.AuthService;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.util.EmailNormalizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.context.ApplicationEventPublisher;
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
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationChallengeManager emailVerificationChallengeManager;
    private final ApplicationEventPublisher eventPublisher;

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
            summary = "Iniciar registro",
            description = "Envia un codigo de verificacion por correo. No crea la cuenta ni emite tokens hasta confirmar el codigo.",
            operationId = "startRegistration",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "202", description = "Codigo de verificacion enviado"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos")
            }
    )
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterVerificationRequest request) {
        emailVerificationService.issueChallenge(request.getEmail(), EmailVerificationPurpose.REGISTRATION);
        return ResponseEntity.accepted().build();
    }

    @Operation(
            summary = "Confirmar registro",
            description = "Crea la cuenta local verificada una vez validado el codigo de seis digitos. No inicia sesion.",
            operationId = "verifyRegistration",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Cuenta creada"),
                    @ApiResponse(responseCode = "400", description = "Codigo invalido")
            }
    )
    @PostMapping("/register/verify")
    public ResponseEntity<Void> verifyRegistration(@Valid @RequestBody RegisterVerificationRequest request) {
        EmailVerificationChallengeManager.VerificationResult result =
                emailVerificationService.verifyChallenge(
                        request.getEmail(), EmailVerificationPurpose.REGISTRATION, request.getCode());
        if (result == EmailVerificationChallengeManager.VerificationResult.VERIFIED) {
            UserRequest userRequest = new UserRequest();
            userRequest.setName(request.getName());
            userRequest.setLastname(request.getLastname());
            userRequest.setEmail(request.getEmail());
            userRequest.setPassword(request.getPassword());
            try {
                userService.registerVerified(userRequest);
            } catch (IllegalArgumentException ignored) {
                // The account already exists; confirmation still succeeds because the email is now verified.
            }
        }
        return verificationResponse(result);
    }

    @Operation(
            summary = "Reenviar código de verificación",
            description = "Envia un nuevo código para una cuenta local no verificada. La respuesta no revela si el correo existe.",
            operationId = "resendEmailVerification",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "202", description = "Solicitud aceptada"),
                    @ApiResponse(responseCode = "400", description = "Datos invalidos")
            }
    )
    @PostMapping("/email-verification/resend")
    public ResponseEntity<Void> resendEmailVerification(
            @Valid @RequestBody EmailVerificationResendRequest request) {
        emailVerificationService.issueChallenge(request.getEmail(), EmailVerificationPurpose.EXISTING_ACCOUNT);
        return ResponseEntity.accepted().build();
    }

    @Operation(
            summary = "Confirmar correo",
            description = "Marca como verificada una cuenta local existente usando el código de seis digitos.",
            operationId = "confirmEmailVerification",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Correo confirmado"),
                    @ApiResponse(responseCode = "400", description = "Codigo invalido")
            }
    )
    @PostMapping("/email-verification/confirm")
    public ResponseEntity<Void> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationResendRequest request) {
        EmailVerificationChallengeManager.VerificationResult result =
                emailVerificationService.verifyChallenge(
                        request.getEmail(), EmailVerificationPurpose.EXISTING_ACCOUNT, request.getCode());
        if (result == EmailVerificationChallengeManager.VerificationResult.VERIFIED) {
            userRepository.findByEmail(EmailNormalizer.normalize(request.getEmail()))
                    .ifPresent(user -> {
                        user.setEmailVerified(true);
                        userRepository.save(user);
                    });
        }
        return verificationResponse(result);
    }

    private ResponseEntity<Void> verificationResponse(
            EmailVerificationChallengeManager.VerificationResult result) {
        return switch (result) {
            case VERIFIED -> ResponseEntity.noContent().build();
            case EXPIRED -> ResponseEntity.badRequest().header("X-Email-Verification-Status", "EXPIRED").build();
            case ATTEMPTS_EXCEEDED ->
                    ResponseEntity.badRequest().header("X-Email-Verification-Status", "ATTEMPTS_EXCEEDED").build();
            case INVALID_CODE -> ResponseEntity.badRequest().header("X-Email-Verification-Status", "INVALID_CODE").build();
        };
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
            description = "Revoca la sesion asociada al refresh token y, si se envía, elimina el token FCM de ese dispositivo.",
            operationId = "logout",
            tags = {"Auth"},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Sesion revocada")
            }
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authTokenService.revokeAndGetUserId(request.getRefreshToken())
                .filter(userId -> request.getFcmToken() != null && !request.getFcmToken().isBlank())
                .ifPresent(userId -> eventPublisher.publishEvent(
                        new UserLoggedOutEvent(userId, request.getFcmToken())));
        return ResponseEntity.noContent().build();
    }
}
