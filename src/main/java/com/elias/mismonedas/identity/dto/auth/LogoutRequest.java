package com.elias.mismonedas.identity.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para revocar una sesión y, opcionalmente, su token FCM de dispositivo")
public class LogoutRequest {

    @NotBlank
    @Schema(description = "Refresh token emitido por el servidor", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

    @Size(max = 500)
    @Schema(description = "Token FCM del dispositivo que cierra sesión")
    private String fcmToken;
}
