package com.elias.mismonedas.identity.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Solicitud para enviar un codigo de restablecimiento de contrasena")
public class PasswordResetRequest {

    @Schema(description = "Correo electronico de la cuenta", example = "usuario@example.com")
    @Email
    @NotBlank
    @Size(max = 320)
    private String email;
}
