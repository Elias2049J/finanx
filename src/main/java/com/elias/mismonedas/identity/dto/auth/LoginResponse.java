package com.elias.mismonedas.identity.dto.auth;

import com.elias.mismonedas.identity.dto.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Respuesta de login que contiene el usuario autenticado y el token JWT")
public class LoginResponse {

    @Schema(
            description = "Informacion del usuario autenticado",
            implementation = UserResponse.class
    )
    private UserResponse user;

    @Schema(
            description = "Token JWT generado tras la autenticacion",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    )
    private String token;
}

