package com.elias.finanx.dto.user;

import com.elias.finanx.entity.enums.TimeZone;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import jakarta.validation.constraints.*;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para registrar un nuevo usuario en el sistema")
public class UserRequest {

    @NotBlank
    @Schema(
            description = "Nombres del usuario",
            example = "Jorge Jackson"
    )
    private String name;

    @NotBlank
    @Schema(
            description = "Apellidos del usuario",
            example = "Valdivia Torreon"
    )
    private String lastname;

    @Email
    @NotBlank
    @Schema(
            description = "Correo electronico del usuario",
            example = "jorge.valdivia@example.com"
    )
    private String email;

    @NotBlank
    @Schema(
            description = "Nombre de usuario unico",
            example = "UsuarioFin2026"
    )
    private String username;

    @NotBlank
    @Schema(
            description = "Contraseña del usuario",
            example = "StrongPassword123!"
    )
    private String password;

    @NotNull
    @Schema(
            description = "Zona horaria preferida del usuario",
            implementation = TimeZone.class,
            example = "AMERICA_LIMA"
    )
    private TimeZone timeZone;
}
