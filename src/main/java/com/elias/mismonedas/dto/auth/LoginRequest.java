package com.elias.mismonedas.dto.auth;

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
@Schema(description = "Solicitud de login interno con email y contrasena")
public class LoginRequest {

    @NotBlank
    @Schema(
            description = "Correo electronico del usuario",
            example = "usuario@example.com"
    )
    private String email;

    @NotBlank
    @Schema(
            description = "Contrasena del usuario",
            example = "MiContrasenaSegura123"
    )
    private String password;
}


