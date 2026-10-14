package com.elias.mismonedas.identity.dto.user;

import com.elias.mismonedas.identity.domain.enums.TimeZone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para actualizar preferencias del usuario")
public class UserPreferencesRequest {

    @NotNull
    @Schema(description = "Zona horaria preferida", implementation = TimeZone.class, example = "AMERICA_LIMA")
    private TimeZone timeZone;

    @NotBlank
    @Size(min = 3, max = 3)
    @Schema(description = "Codigo de moneda por defecto", example = "PEN")
    private String currencyCode;

    @Schema(description = "Notificaciones por email habilitadas", example = "true")
    private boolean emailNotifications;
}
