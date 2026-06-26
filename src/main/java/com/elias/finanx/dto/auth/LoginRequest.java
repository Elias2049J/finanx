package com.elias.finanx.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "Solicitud de login interno con email y contraseña")
public class LoginRequest {

    @NotBlank
    @Schema(
            description = "Correo electronico del usuario",
            example = "usuario@example.com"
    )
    private String email;

    @NotBlank
    @Schema(
            description = "Contraseña del usuario",
            example = "MiContraseñaSegura123"
    )
    private String password;
}


