package com.elias.finanx.dto.user;

import com.elias.finanx.entity.enums.TimeZone;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Preferencias del usuario")
public class UserPreferencesResponse {

    @Schema(description = "Identificador unico", example = "1")
    private Long id;

    @Schema(description = "Zona horaria preferida", implementation = TimeZone.class, example = "AMERICA_LIMA")
    private TimeZone timeZone;

    @Schema(description = "Codigo de moneda por defecto", example = "PEN")
    private String currencyCode;

    @Schema(description = "Notificaciones por email habilitadas", example = "true")
    private boolean emailNotifications;

    @Schema(description = "Tokens FCM registrados", example = "[\"fM8x...abc123\"]")
    private List<String> fcmTokens;

    @Schema(description = "Fecha de creacion", example = "2026-06-25T23:59:59")
    private LocalDateTime createdAt;

    @Schema(description = "Ultima actualizacion", example = "2026-07-01T12:00:00")
    private LocalDateTime updatedAt;
}
