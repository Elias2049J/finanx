package com.elias.mismonedas.identity.dto.auth;

import com.elias.mismonedas.identity.dto.user.UserRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Datos para iniciar o confirmar el registro mediante código de verificación")
public class RegisterVerificationRequest {

    @Schema(description = "Nombres del usuario", example = "Jorge Jackson")
    @NotBlank
    private String name;

    @Schema(description = "Apellidos del usuario", example = "Valdivia Torreon")
    @NotBlank
    private String lastname;

    @Schema(description = "Correo electrónico del usuario", example = "jorge.valdivia@example.com")
    @Email
    @NotBlank
    private String email;

    @Schema(description = "Contraseña del usuario", example = "StrongPassword123!")
    @NotBlank
    @Size(min = 6, max = 255)
    private String password;

    @Schema(description = "Código de seis dígitos enviado al correo", example = "123456")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener exactamente seis dígitos")
    private String code;
}
