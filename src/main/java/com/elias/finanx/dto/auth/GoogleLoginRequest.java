package com.elias.finanx.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Solicitud de login con Google, contiene el ID Token emitido por Google Identity")
public class GoogleLoginRequest {

    @NotBlank
    @Schema(
            description = "ID Token JWT emitido por Google tras la autenticacion",
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE2Y..."
    )
    private String idToken;
}


