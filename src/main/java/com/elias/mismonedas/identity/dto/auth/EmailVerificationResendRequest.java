package com.elias.mismonedas.identity.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Datos para reenviar o confirmar un código de verificación de email")
public class EmailVerificationResendRequest {

    @Schema(description = "Correo electrónico del usuario", example = "jorge.valdivia@example.com")
    @Email
    @NotBlank
    private String email;

    @Schema(description = "Código de seis dígitos enviado al correo", example = "123456")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener exactamente seis dígitos")
    private String code;
}
