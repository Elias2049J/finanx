package com.elias.mismonedas.notifications.dto.fcm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta con informacion de un token FCM registrado")
public class FcmTokenResponse {

    @Schema(description = "Identificador unico del token", example = "1")
    private Long id;

    @Schema(description = "Token FCM", example = "fM8x...abc123")
    private String token;

    @Schema(description = "Nombre del dispositivo", example = "Samsung Galaxy S24")
    private String deviceName;

    @Schema(description = "Fecha de creacion", example = "2026-06-25T23:59:59")
    private LocalDateTime createdAt;

    @Schema(description = "Ultima actualizacion", example = "2026-07-01T12:00:00")
    private LocalDateTime updatedAt;
}
