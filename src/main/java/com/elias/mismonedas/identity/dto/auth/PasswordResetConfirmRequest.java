package com.elias.mismonedas.identity.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Confirmacion del restablecimiento de contrasena con el codigo enviado por correo")
public class PasswordResetConfirmRequest {

    @Schema(description = "Correo electronico de la cuenta", example = "usuario@example.com")
    @Email
    @NotBlank
    @Size(max = 320)
    private String email;

    @Schema(description = "Codigo de seis digitos enviado al correo", example = "123456")
    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "El codigo debe tener exactamente seis digitos")
    private String code;

    @Schema(description = "Nueva contrasena de la cuenta", example = "NuevaClave123!")
    @NotBlank
    @Size(min = 6, max = 255)
    private String newPassword;
}
