package com.elias.finanx.dto.fcm;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para registrar un token FCM")
public class RegisterFcmTokenRequest {

    @NotBlank
    @Schema(description = "Token FCM del dispositivo", example = "fM8x...abc123")
    private String token;

    @Schema(description = "Nombre descriptivo del dispositivo", example = "Samsung Galaxy S24")
    private String deviceName;
}
