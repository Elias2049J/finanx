package com.elias.mismonedas.identity.dto.user;

import com.elias.mismonedas.identity.domain.enums.TimeZone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Pattern(regexp = "(?i)^(es-PE|en-US)$", message = "Locale soportado: es-PE o en-US")
    @Schema(description = "Locale preferido (etiqueta BCP 47)", allowableValues = {"es-PE", "en-US"}, example = "es-PE")
    private String locale;

    @Schema(description = "Notificaciones por email habilitadas", example = "true")
    private boolean emailNotifications;
}
